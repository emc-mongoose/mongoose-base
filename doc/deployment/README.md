# Deployment

1. [Environment Requirements](#environment-requirements)<br/>
2. [Jar](#jar)<br/>
3. [Docker](#docker)<br/>
    3.1. [Standalone](#standalone)<br/>
    &nbsp;&nbsp;&nbsp;&nbsp;3.1.1 [Mount files](#mount-files)<br/>
    3.2. [Distributed Mode](#distributed-mode)<br/>
    &nbsp;&nbsp;&nbsp;&nbsp;3.2.1 [Custom ports](#custom-ports)<br/>
    &nbsp;&nbsp;&nbsp;&nbsp;3.2.2 [2 docker containers on 1 machine](#2-docker-containers-on-1-machine)<br/>
    3.3 [Additional Notes](#additional-notes)<br/>
    &nbsp;&nbsp;&nbsp;&nbsp;3.3.1 [Logs Sharing](#logs-sharing)<br/>
    &nbsp;&nbsp;&nbsp;&nbsp;3.3.2 [Debugging](#debugging)<br/>
    3.4. [Docker-compose](#docker-compose)<br/>
    3.5. [Docker-swarm](#docker-swarm)<br/>
4. [Kubernetes](#kubernetes)<br/>
  1. [Helm](#helm)<br/>

---

# Environment Requirements

* Java 11-14 (except 11.0.8 - has an issue with RMI) or Docker
* OS open files limit is at least a bit higher than specified concurrency level
* Few gigabytes of free memory.

High-load tests may allocate up to few GBs of the memory depending on the scenario.
* (Remote Storage) Connectivity with the endpoint nodes via the ports used
* (Distributed Mode) Connectivity with the additional/remote nodes via port #1099 (RMI)
* (Remote Monitoring) Connectivity with the nodes via port #9010 (JMX)

---

# Jar

Mongoose is distributed as a single jar file from:
http://central.maven.org/maven2/com/github/emc-mongoose/mongoose-base/

[About bundle jars](https://github.com/emc-mongoose/mongoose#backward-compatibility-notes)

---

# Docker

Mongoose images are stored in the [Docker Hub](https://hub.docker.com/u/emcmongoose/)

## Base image

**Note**
> The base image doesn't contain any additonal load step types neither additional storage drivers. Please use one of the
> specific images either consider using the [backward compatibility bundle](https://github.com/emc-mongoose/mongoose)

## Standalone

The image may be used in the standalone mode:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    [<MONGOOSE CLI ARGS>]
```

### Mount files

An example for mounting and using a scenario. Thus, files for input/output, configurations, logs and metrics can be mounted.

```
docker run -d --network host  \
    -v /path/to/scenario.js:/opt/scenario.js \
    emcmongoose/mongoose[-<TYPE>] \
    --run-scenario=/opt/scenario.js
``` 

**Mounting** a volume **is the only right way** to save Mongoose logs in docker. Don't get confused by [`--output-file` option](../usage/output#127-item-list-files), it's not about docker.

## Distributed Mode

#### Node

First, it's necessary to start some node/peer services.

Additional node run command:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --run-node 
```

#### Run

To invoke the run in the distributed mode it's necessary to specify the additional node/peer addresses.

Entry node run command:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --load-step-node-addrs=<ADDR1,ADDR2:PORT,ADDR3...> \
    [<MONGOOSE CLI ARGS>]
```

#### Custom ports

**NOTE** 
> Mongoose uses `1099` port for RMI between mongoose nodes and `9999` for REST API. If you run several mongoose nodes on the same host (in different docker containers, for example) or if the ports are used by another service, then ports can be redefined:

**Additional node:**
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --run-node \
    --load-step-node-port=<CUSTOM RMI PORT> \
    --run-port=<CUSTOM HTTP PORT> 
 ```

**Entry node:**
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --load-step-node-addrs=ADDR:<CUSTOM RMI PORT> \
    [<MONGOOSE CLI ARGS>]
```

**NOTE** 
> If port didn't specified, then `1099` will be used by default.

#### 2 docker containers on 1 machine
> Note 1: E = Entry node, A = Additional node, D = Address used in defaults.yaml

> Note 2: To get Internal IP of container: `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' [DOCKER ID/NAME]`

| It works | It doesn't work |
| --- | --- |
| E: `--network host` <br></br> A: `-p rest2:rest -p rmi2:rmi` <br></br> D: `localhost:rmi2`| E: `--network host` <br></br> A: `--network host` <br></br> D: `localhost:rmi`|
| E: `--network host` <br></br> A: `None (used docker bridge)` <br></br> D: `Internal IP`| E: `-p rest1:rest -p rmi1:rmi` <br></br> A: `-p rest2:rest -p rmi2:rmi` <br></br> D: `localhost:rmi2`|
| E: `-p rest1:rest` <br></br> A: `None (used docker bridge)` <br></br> D: `Internal IP`| |
| E: `-p rest1:rest` <br></br> A: `-p rest2:rest` <br></br> D: `Internal IP`| |

## Additional Notes

#### Logs Sharing

The example below mounts the host's directory `./log` to the container's
`/root/.mongoose/<VERSION>/log` (where mongoose holds its log files).

```bash
docker run \
    --network host \
    --mount type=bind,source="$(pwd)"/log,target=/root/.mongoose/<VERSION>/log
    emcmongoose/<IMAGE> \
    [<MONGOOSE CLI ARGS>]
```

#### Debugging

The example below starts the Mongoose in the container with remote
debugging capability via the port #5005.

At first it's need to build new docker image for debug:


```bash
docker build --build-arg MONGOOSE_VERSION=latest -f ci/docker/Dockerfile.debug -t emcmongoose/<IMAGE>:debug .
```
or with `MONGOOSE_VERSION` value bt default (latest):
```bash
docker build -f ci/docker/Dockerfile.debug -t emcmongoose/<IMAGE>:debug .
```

and run:

```bash
docker run \
    --network host \
    emcmongoose/<IMAGE>:debug \
    [<MONGOOSE CLI ARGS>]
```

## Docker-compose 

> *Checked with Docker version: 19.03.8*

#### Deploy only mongoose nodes
Change `.env` file to configure image and project name.
```bash
docker-compose up -d --scale mongoose-node=3
```
Check:
```bash
# docker ps
CONTAINER ID        IMAGE                                      COMMAND                  CREATED             STATUS              PORTS                                            NAMES
6e3ec1f837c8        emcmongoose/mongoose-base:latest           "/opt/mongoose/entry…"   14 seconds ago      Up 12 seconds       0.0.0.0:1091->1099/tcp, 0.0.0.0:9991->9999/tcp   mongoose_mongoose-node_3
f671b77ffd27        emcmongoose/mongoose-base:latest           "/opt/mongoose/entry…"   14 seconds ago      Up 12 seconds       0.0.0.0:1093->1099/tcp, 0.0.0.0:9993->9999/tcp   mongoose_mongoose-node_2
40255c0a91d9        emcmongoose/mongoose-base:latest           "/opt/mongoose/entry…"   14 seconds ago      Up 12 seconds       0.0.0.0:1092->1099/tcp, 0.0.0.0:9992->9999/tcp   mongoose_mongoose-node_1
...
```

#### Start entry-node (or with [REST API](doc/interfaces/api/remote)):
```bash
docker run -d --name mongoose \
              --network host \
              emcmongoose/mongoose-base:latest \
            --load-step-node-addrs=localhost:1091,localhost:1092,localhost:1093
```

or with created network `mongoose_default`:
```bash
docker run -d --name mongoose \
              --network mongoose_default \
              emcmongoose/mongoose-base:latest \
            --load-step-node-addrs=mongoose_mongoose-node_1,mongoose_mongoose-node_2,mongoose_mongoose-node_3
```

## Docker-swarm

> *Checked with Docker version: 19.03.8*

#### Create docker swarm cluster

*prerequisites*: node1(ip1), node2(ip2), node3(ip3)

ssh to node1:
```
docker swarm init
### to display token
docker swarm join-token -q worker
```
ssh to node2, node2
```
docker swarm join --token <some token> <ip1>:2377
```

#### Deploy mongoose nodes
Change `.env` file to configure image and project name.
```
docker stack deploy --compose-file docker-swarm.yaml mongoose-nodes
```
```
$ docker stack ps mongoose-nodes
 ID                  NAME                       IMAGE                              NODE                DESIRED STATE       CURRENT STATE           ERROR               PORTS
 sy6krxo9vnj3        mongoose-nodes_mongoose-node.1   emcmongoose/mongoose-base:latest   node5               Running             Running 1 second ago

$ curl -I node5:9999/run
HTTP/1.1 204 No Content
...
```
change mongoose replicas count:
```
export REPLICAS=3; docker stack deploy --compose-file docker-swarm.yaml mongoose-nodes
```
```
$ docker stack ps mongoose-nodes
ID                  NAME                       IMAGE                              NODE                DESIRED STATE       CURRENT STATE           ERROR               PORTS
sy6krxo9vnj3        mongoose-nodes_mongoose-node.1   emcmongoose/mongoose-base:latest   node5               Running             Running 1 second ago
6m9d04e75ybd        mongoose-nodes_mongoose-node.2   emcmongoose/mongoose-base:latest   node4               Running             Running 3 seconds ago
x7euup6ihumb        mongoose-nodes_mongoose-node.3   emcmongoose/mongoose-base:latest   node6               Running             Running 2 seconds ago
```

also you can specify `IMAGE` and `TAG` to use custom mongoose docker image:tag

#### Destroy mongoose nodes

```bash
docker stack rm mongoose-nodes
```
---

# Kubernetes

Mongoose can be deployed in a [kubernetes](https://kubernetes.io/) cluster manually or with Helm. 

## Helm

The only officially supported way to deploy Mongoose in Kubernetes is through Helm charts.

[Mongoose Helm chart doc](https://github.com/emc-mongoose/mongoose-helm-charts)

## Logs

With command `kubectl logs -n mongoose <resource name>` you can see logs into container. For example:

```bash
$ kubectl logs -n mongoose mongoose
################################################### mongoose v 4.2.7 ###################################################
2019-04-08T09:41:52,777 I                                main                           Available/installed extensions:
	Load --------------------------> com.emc.mongoose.base.load.step.linear.LinearLoadStepExtension
	dummy-mock --------------------> com.emc.mongoose.base.storage.driver.mock.DummyStorageDriverMockExtension
	http --------------------------> com.emc.mongoose.storage.driver.coop.netty.http.HttpStorageDriverExtension
	s3 ----------------------------> com.emc.mongoose.storage.driver.coop.netty.http.s3.S3StorageDriverExtension
	WeightedLoad ------------------> com.emc.mongoose.load.step.weighted.WeightedLoadStepExtension
	PipelineLoad ------------------> com.emc.mongoose.load.step.pipeline.PipelineLoadStepExtension
	atmos -------------------------> com.emc.mongoose.storage.driver.coop.netty.http.atmos.AtmosStorageDriverExtension
...
```

## Deleting kubernetes resources

There are several ways to delete kubernetes resources:
* delete helm release `helm uninstall mongoose`
* delete by configuration `kubectl delete -f <filename>.yaml`
* manual removal `kubectl delete -n mongoose pod NAME`
* removal of all resources in namespace `kubectl delete -n mongoose pod --all`
* removal of namespace (including resources) `kubectl delete namespace mongoose`
