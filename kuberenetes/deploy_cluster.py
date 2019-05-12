import os
import re
import subprocess
import sys
import time
from fabric import Connection
from invoke import Responder
# Variables:
# host_login = 'kube'
# host_password = '123'
# host_login = 'emc'
# host_password = 'emc'
# host_login = 'user'
# host_password = '1208'
# root_login = 'root'
# root_password = "abc123"
# sudopass = Responder(pattern=r'\[sudo\] password for', response=host_password + '\n', )
# root_passwd_enter = Responder(pattern=r'Enter| new Unix password', response=root_password + '\n', )
# root_passwd_retype = Responder(pattern=r'Retype| new Unix password', response=root_password + '\n', )

# URLS = ['etcd.tar.bz2',
# 		'kube-apiserver.tar.bz2',
# 		'kube-controller-manager.tar.bz2',
# 		'kube-proxy.tar.bz2',
# 		'kube-scheduler.tar.bz2',
# 		'pause.tar.bz2',
# 		'coredns.tar.bz2',
# 		'flannel.tar.bz2']

IMAGES = [
	'k8s.gcr.io/kube-apiserver:v1.13.5',
	'k8s.gcr.io/kube-controller-manager:v1.13.5"',
	'k8s.gcr.io/kube-scheduler:v1.13.5',
	'k8s.gcr.io/kube-proxy:v1.13.5',
	'k8s.gcr.io/pause:3.1',
	'k8s.gcr.io/etcd:3.2.24',
	'k8s.gcr.io/coredns:1.2.6'
]

PATH_TO_CA = "./ca/CA.pem"
PATH_TO_KUBE_REPO = "./kubernetes.repo"
PATH_TO_IMAGES = "./images/"


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
	def connect(self, host_ip, login, password):
		return Connection(host=login + "@" + host_ip,
						  connect_kwargs={"password": password})
	def run_shell_command(self, hosts_ip, credentials, command, stdout=False):
		# Run sh commands on hosts
		for host_ip, credentials in zip(hosts_ip, credentials):
			login = credentials[0]
			password = credentials[1]
			print(bcolors.BLUE + 'Hostname is: ' + host_ip + bcolors.ENDC)
			sudopass = Responder(pattern=r'\[sudo\] password for', response=password + '\n', )
			root_passwd_enter = Responder(pattern=r'Enter| new Unix password', response=password + '\n', )
			root_passwd_retype = Responder(pattern=r'Retype| new Unix password', response=password + '\n', )
			if stdout:
				return self.connect(host_ip, login, password).run(command, pty=True, watchers=[sudopass]).stdout.strip()
			else:
				self.connect(host_ip, login, password) \
					.run(command, pty=True, watchers=[sudopass, root_passwd_enter, root_passwd_retype])
	def copy_file_to_host(self, hosts_ip, credentials, file_name, path_on_host):
		# Copy file current machine to hosts
		for host_ip, credentials in zip(hosts_ip, credentials):
			login = credentials[0]
			password = credentials[1]
			self.connect(host_ip, login, password).put(file_name, remote=path_on_host)
	def download_file_on_host(self, hosts_ip, credentials, url):
		self.run_shell_command(hosts_ip, credentials, "wget -p " + url, stdout=True)
	def close(self, hosts_ip, credentials):
		for host_ip, credentials in zip(hosts_ip, credentials):
			login = credentials[0]
			password = credentials[1]
			self.connect(host_ip, login, password).close()
			print('Close connection for non-root user')


ssh = Operations()


# Functions
def reconfigure_ssh_and_root_passwd(hosts_ip, credentials):
	ssh_command = "sudo apt install -y ssh"
	ssh_command += "\nsudo passwd root"
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)
	ssh.copy_file_to_host(hosts_ip, credentials, "sshd_config", "/tmp/")
	ssh_command = "sudo cp -r /tmp/sshd_config /etc/ssh/"
	ssh_command += "\nsudo systemctl restart sshd"
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)


def install_env(hosts_ip, credentials):
	# Update and install default package
	ssh_command = '''
		yum update -y
		yum install -y docker curl unzip numactl binutils
	'''
	# Restart docker
	ssh_command += '''
		systemctl enable docker
		systemctl restart docker
	'''
	# Install linux headers
	# ssh_command += '\nyum install -y linux-headers-4.4.0-140 linux-headers-4.4.0-140-generic'
	# Disable swap on host
	ssh_command += '''
		swapoff -a
		sudo sed -i '/ swap / s/^/#/' /etc/fstab
	'''
	# Disable SELinux:
	ssh_command += '''
		setenforce 0
		sed -i s/^SELINUX=.*$/SELINUX=disabled/ /etc/selinux/config
	'''
	# Disable Firewall
	ssh_command += '''
		systemctl disable firewalld
		systemctl stop firewalld
		iptables -L
		echo 'net.bridge.bridge-nf-call-iptables = 1' > /etc/sysctl.d/87-sysctl.conf
	'''
	# Add certificate if they exist in ./ca/
	if os.path.isfile(PATH_TO_CA):
		ssh.copy_file_to_host(hosts_ip, credentials, PATH_TO_CA, "/etc/pki/ca-trust/source/anchors/CA.pem")
		ssh.copy_file_to_host(hosts_ip, credentials, PATH_TO_CA, "/")
		ssh_command += '\nupdate-ca-trust'  # '\nupdate-ca-certificates'
	# Install kubernetes
	ssh_command += '''
		rm -rf /etc/yum.repos.d/kubernetes.repo
	'''
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)
	ssh.copy_file_to_host(hosts_ip, credentials, PATH_TO_KUBE_REPO, "/etc/yum.repos.d/kubernetes.repo")
	ssh_command = '''
        yum install -y kubelet kubeadm kubectl
        export KUBECONFIG=/etc/kubernetes/kubelet.conf
        echo 'Environment="KUBELET_EXTRA_ARGS=--fail-swap-on=false"' | tee -a /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
        systemctl daemon-reload && systemctl enable kubelet && systemctl start kubelet
		rm -rf /var/lib/kubelet/pki /var/lib/etcd
        yes | kubeadm reset
    '''
	#### see above #### TODO
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)


def copy_and_load_images(hosts_ip, credentials):
	print('Download images on host: ')
	path_on_host = '/tmp/kubernetes'
	ssh_command = 'mkdir -p ' + path_on_host
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)
	files = os.listdir(PATH_TO_IMAGES)
	for file in files:
		print(bcolors.BLUE + 'Upload: ' + bcolors.ENDC + bcolors.GREEN + file + bcolors.ENDC)
		path_to_file = PATH_TO_IMAGES + '/' + file
		ssh.copy_file_to_host(hosts_ip, credentials, path_to_file, path_on_host)
	#
	ssh_command = '''
        cd /tmp/kubernetes
        for image in $(ls); do docker import ${image} ${image}; done;
    	rm -rf /tmp/kubernetes
    '''
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)


def master_kube_init(hosts_ip, credentials):
	global token_id
	master_ip = []
	master_ip.append(hosts_ip[0])
	# Init cluster for kubernetes
	ssh_command = '''
        echo '\n\nStart pull images for kubeadm:\n'
        
        kubeadm init --pod-network-cidr=10.244.0.0/16 --ignore-preflight-errors Swap
        
        chmod 604 /etc/kubernetes/admin.conf
        mkdir -p $HOME/.kube
        chown $(id -u):$(id -g) /etc/kubernetes/admin.conf
		cp -i /etc/kubernetes/admin.conf $HOME/.kube/config

        kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
    	kubectl taint nodes --all node-role.kubernetes.io/master-
    '''
	token_command = "kubeadm token list | awk 'END {print $1}'"
	ssh.run_shell_command(master_ip, credentials, ssh_command)
	token_id = ssh.run_shell_command(master_ip, credentials, token_command, stdout=True)


def add_slave_to_master(list_of_host_ip, credentials):
	master_ip = list_of_host_ip[0]
	hosts_ip = list_of_host_ip[1:]
	print(bcolors.BLUE + "\nMaster ip: " + master_ip + bcolors.ENDC)
	print(bcolors.BLUE + "Token id: " + token_id + bcolors.ENDC)
	# Add slave nodes to master
	# ssh_command = 'systemctl start docker.service'
	ssh_command = 'kubeadm join --token ' + token_id + ' ' + master_ip + ':6443 --ignore-preflight-errors=Swap ' \
																		 '--discovery-token-unsafe-skip-ca-verification'
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)
	ssh_command = '''
	export KUBECONFIG=/etc/kubernetes/kubelet.conf
	cat > /etc/cni/net.d/10-flannel.conflist <<EOF
	{
		"name": "cbr0",
		"plugins": [
			{
				"type": "flannel",
				"delegate": {
					"hairpinMode": true,
					"isDefaultGateway": true
				}
			},
			{
				"type": "portmap",
				"capabilities": {
					"portMappings": true
				}
			}
		]
	}
	
	EOF
	'''
	ssh.run_shell_command(hosts_ip, credentials, ssh_command)


def parse_ip(list_of_hosts):
	return list(map(lambda str: re.split('@|:', str)[1], list_of_hosts))


def parse_credentials(list_of_hosts):
	split = lambda str: re.split('@|:', str)
	return list(map(lambda str: [split(str)[0], split(str)[2]], list_of_hosts))


def main(list_of_host_ip, credentials):
	status = ssh.ping(list_of_host_ip)
	if status:
		# reconfigure_ssh_and_root_passwd(list_of_host_ip)
		install_env(list_of_host_ip, credentials)
		# if there are problems with pooling images with https://storage.googleapis.com/
		copy_and_load_images(list_of_host_ip, credentials)
		master_kube_init(list_of_host_ip, credentials)
	# add_slave_to_master(list_of_host_ip, credentials)
	# close all connections
	# ssh.close(list_of_host_ip, credentials)
	else:
		print(bcolors.RED
			  + '\nSome of the hosts are offline.\nPlease check the availability of the hosts and run the script again.')


if __name__ == "__main__":
	# TODO: request enter your (host) credentials
	# TODO: make available set config file
	if len(sys.argv) == 1:
		print(bcolors.RED + "Please enter the address of at least one host.")
	# Start main function with list of input hosts
	else:
		# TODO : add MSG if except pass or login
		ips = parse_ip(sys.argv[1:])
		credentials = parse_credentials(sys.argv[1:])
		main(ips, credentials)
	print(bcolors.ENDC)



'''
docker pull k8s.gcr.io/kube-apiserver:v1.13.5
docker pull k8s.gcr.io/kube-controller-manager:v1.13.5
docker pull k8s.gcr.io/kube-scheduler:v1.13.5
docker pull k8s.gcr.io/kube-proxy:v1.13.5
docker pull k8s.gcr.io/pause:3.1
docker pull k8s.gcr.io/etcd:3.2.24
docker pull k8s.gcr.io/coredns:1.2.6
'''
