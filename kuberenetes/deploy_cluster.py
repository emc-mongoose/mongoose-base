import os
import sys
import time
import subprocess
from fabric import Connection
from invoke import Responder


# Variables:
# host_login = 'kube'
# host_password = '123'
# host_login = 'emc'
# host_password = 'emc'
host_login = 'emc'
host_password = 'ChangeMe'
root_login = 'root'
root_password = "ChangeMe"
sudopass = Responder(pattern=r'\[sudo\] password for', response=host_password + '\n',)
root_passwd_enter = Responder(pattern=r'Enter| new Unix password', response=root_password + '\n',)
root_passwd_retype = Responder(pattern=r'Retype| new Unix password', response=root_password + '\n',)


class bcolors:
    RED = '\033[31m'
    GREEN = '\033[32m'
    BLUE = '\033[94m'
    ENDC = '\033[0m'


class Operations:
    def __init__(self):
        pass

    def ping(self, hosts_ip):
        # Check hosts online
        status = True
        status_print = bcolors.BLUE + 'Status of hosts:\n'
        for host_ip in hosts_ip:
            with open(os.devnull, "wb") as limbo:
                result = subprocess.Popen(["ping", "-c", "1", "-n", "-W", "2", host_ip],
                                          stdout=limbo, stderr=limbo).wait()
                if result:
                    status_print += bcolors.RED + host_ip + '\n' + bcolors.ENDC
                    status = False
                else:
                    status_print += bcolors.GREEN + host_ip + '\n' + bcolors.ENDC
                time.sleep(2)
        print(status_print)
        return status

    def connect(self, host_ip, root=True):
        # Connect to hosts via ssh
        global root_login, root_password
        if not root:
            root_login = host_login
            root_password = host_password
        return Connection(host=root_login + "@" + host_ip,
                          connect_kwargs={"password": root_password})

    def run_shell_command(self, hosts_ip, command, stdout=False, root=True):
        # Run sh commands on hosts
        if not root:
            root = False
        for host_ip in hosts_ip:
            print(bcolors.BLUE + 'Hostname is: ' + host_ip + bcolors.ENDC)
            if stdout:
                return self.connect(host_ip).run(command, pty=True, watchers=[sudopass]).stdout.strip()
            else:
                self.connect(host_ip, root).run(command, pty=True, watchers=[sudopass, root_passwd_enter, root_passwd_retype])

    def copy_file_to_host(self, hosts_ip, file_name, path_on_host, root=True):
        # Copy file current machine to hosts
        if not root:
            root = False
        for host_ip in hosts_ip:
            self.connect(host_ip, root).put(file_name, remote=path_on_host)

    def close(self, hosts_ip):
        for host_ip in hosts_ip:
            self.connect(host_ip, root=False).close()
            print('Close connection for non-root user')


ssh = Operations()


# Functions
def reconfigure_ssh_and_root_passwd(hosts_ip):
    ssh_command = "sudo apt install -y ssh"
    ssh_command += "\nsudo passwd root"
    ssh.run_shell_command(hosts_ip, ssh_command, root=False)
    ssh.copy_file_to_host(hosts_ip, "sshd_config", "/tmp/", root=False)
    ssh_command = "sudo cp -r /tmp/sshd_config /etc/ssh/"
    ssh_command += "\nsudo systemctl restart sshd"
    ssh.run_shell_command(hosts_ip, ssh_command, root=False)


def install_env(hosts_ip):
    # Update and install default package
    ssh_command = '''
        echo "\
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial main restricted \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial-updates main restricted \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial universe \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial-updates universe \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial multiverse \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial-updates multiverse \
        \ndeb http://us.archive.ubuntu.com/ubuntu/ xenial-backports main restricted universe multiverse \
        \ndeb http://security.ubuntu.com/ubuntu xenial-security main restricted \
        \ndeb http://security.ubuntu.com/ubuntu xenial-security universe \
        \ndeb http://security.ubuntu.com/ubuntu xenial-security multiverse" > /etc/apt/sources.list
        apt update
    '''
    ssh_command += '\napt install -y numactl libaio1 openjdk-8-jre apt-transport-https curl binutils'
    # Install linux headers
    ssh_command += '\napt install -y linux-headers-4.4.0-140 linux-headers-4.4.0-140-generic'
    # Disable swap on host
    ssh_command += '\nswapoff -a'
    # Add certificate
    ssh_command += '\nwget -p http://lglaf020.lss.emc.com/ovf/git/EMCSSLDecryptionAuthorityv2.crt -O ' \
                   '/usr/local/share/ca-certificates/EMCSSLDecryptionAuthorityv2.crt'
    ssh_command += '\nwget -p http://lglaf020.lss.emc.com/ovf/git/EMC_SSL_ca.crt -O ' \
                   '/usr/local/share/ca-certificates/EMC_SSL_ca.crt'
    ssh_command += '\nwget -p http://lglaf020.lss.emc.com/ovf/git/EMC_root_ca.crt -O ' \
                   '/usr/local/share/ca-certificates/EMC_root_ca.crt'
    ssh_command += '\ncd /usr/local/share/ca-certificates/ && cat $(ls) >> ca-bundle.crt'
    ssh_command += '\nupdate-ca-certificates'
    # Install docker
    ssh_command += '\napt install -y docker.io'
    # Install kubernetes
    ssh_command += '''
        curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
        echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | tee -a /etc/apt/sources.list.d/kubernetes.list
        apt update
        apt install -y kubelet kubeadm kubectl
        apt-mark hold kubelet kubeadm kubectl
        echo 'Environment="KUBELET_EXTRA_ARGS=--fail-swap-on=false"' | tee -a /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
        systemctl daemon-reload && systemctl restart kubelet
    '''
    ssh.run_shell_command(hosts_ip, ssh_command)


def download_and_load_images(hosts_ip):
    print('Download images on host: ')
    ssh_command = 'mkdir /tmp/kubernetes'
    ssh.run_shell_command(hosts_ip, ssh_command)
    for file_name in ['etcd.tar.bz2', 'kube-apiserver.tar.bz2', 'kube-controller-manager.tar.bz2',
                      'kube-proxy.tar.bz2', 'kube-scheduler.tar.bz2', 'pause.tar.bz2', 'coredns.tar.bz2', 'flannel.tar.bz2']:
        print(bcolors.BLUE + 'Upload: ' + bcolors.ENDC + bcolors.GREEN + file_name + bcolors.ENDC)
        ssh.copy_file_to_host(hosts_ip, 'k8s.gcr.io/' + file_name, '/tmp/kubernetes')
    ssh_command = '''
        cd /tmp/kubernetes
        for image in $(ls); do bunzip2 ${image}; done;
        for image in $(ls); do cat ${image} | docker load; done;
        rm -rf /tmp/kubernetes
    '''
    ssh.run_shell_command(hosts_ip, ssh_command)


def master_kube_init(hosts_ip):
    global token_id
    host_ip = []
    host_ip.append(hosts_ip[0])
    # Init cluster for kubernetes
    # ssh_command = '''
    #     echo 'Start pull images for kubeadm:'
    #     kubeadm init --apiserver-advertise-address=192.168.56.30 --kubernetes-version v1.14.0 --pod-network-cidr=10.244.0.0/16 --ignore-preflight-errors Swap
    #     chmod 604 /etc/kubernetes/admin.conf
    #     mkdir -p $HOME/.kube
    #     cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
    #     chown $(id -u):$(id -g) $HOME/.kube/config
    #     export KUBECONFIG=$HOME/.kube/config
    #     kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
    # '''
    ssh_command = '''
        echo 'Start pull images for kubeadm:'
        kubeadm init --pod-network-cidr=10.244.0.0/16 --ignore-preflight-errors Swap --kubernetes-version v1.14.0
        chmod 604 /etc/kubernetes/admin.conf
        mkdir -p $HOME/.kube
        cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
        chown $(id -u):$(id -g) $HOME/.kube/config
        export KUBECONFIG=$HOME/.kube/config
        kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
    '''
    token_command = "kubeadm token list | awk 'END {print $1}'"
    ssh.run_shell_command(host_ip, ssh_command)
    token_id = ssh.run_shell_command(host_ip, token_command, stdout=True)


def add_slave_to_master(list_of_host_ip):
    master_ip = list_of_host_ip[0]
    hosts_ip = list_of_host_ip[1:]
    print(bcolors.BLUE + "Master ip: " + master_ip + bcolors.ENDC)
    print(bcolors.BLUE + "Token id: " + token_id + bcolors.ENDC)
    # Add slave nodes to master
    ssh_command = 'kubeadm join --token ' + token_id + ' ' + master_ip + ':6443 --ignore-preflight-errors=Swap ' \
                                                                              '--discovery-token-unsafe-skip-ca-verification'
    ssh.run_shell_command(hosts_ip, ssh_command)


def main(list_of_host_ip):
    status = ssh.ping(list_of_host_ip)
    if status:
        # reconfigure_ssh_and_root_passwd(list_of_host_ip)
        install_env(list_of_host_ip)
        download_and_load_images(list_of_host_ip)
        # master_kube_init(list_of_host_ip)
        # add_slave_to_master(list_of_host_ip)
    else:
        print bcolors.RED + '\nSome of the hosts are offline.\nPlease check the availability of the hosts and run the script again.'


if __name__ == "__main__":
    # Start main function with list of input hosts
    main(sys.argv[1:])