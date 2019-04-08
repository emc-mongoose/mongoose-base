# Environment Requirements

* Java 11+ or Docker
* OS open files limit is at least a bit higher than specified concurrency level
* Few gigabytes of free memory.

High-load tests may allocate up to 1-2 GB of the memory depending on the scenario.
* (Remote Storage) Connectivity with the endpoint nodes via the ports used
* (Distributed Mode) Connectivity with the additional/remote nodes via port #1099 (RMI)
* (Remote Monitoring) Connectivity with the nodes via port #9010 (JMX)

# Jar

Mongoose is distributed as a single jar file from:
http://central.maven.org/maven2/com/github/emc-mongoose/mongoose-base/

# Docker

Mongoose images are stored in the [Docker Hub](https://hub.docker.com/u/emcmongoose/)

## Base

**Note**
> The base image doesn't contain any additonal load step types neither additional storage drivers. Please use one of the
> specific images either consider using the [backward compatibility bundle](https://github.com/emc-mongoose/mongoose)

### Standalone

The base image may be used in the standalone mode:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] [\
    <ARGS>]
```

### Distributed Mode

#### Node

First, it's necessary to start some node/peer services:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --run-node [\
    --load-step-node-port=<PORT>]
```

#### Run

To invoke the run in the distributed mode it's necessary to specify the additional node/peer addresses:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --load-step-node-addrs=<ADDR1,ADDR2,...> [\
    <ARGS>]
```

## Additional Notes

### Logs Sharing

The example below mounts the host's directory `./log` to the container's
`/root/.mongoose/<VERSION>/log` (where mongoose holds its log files).

```bash
docker run \
    --network host \
    --mount type=bind,source="$(pwd)"/log,target=/root/.mongoose/<VERSION>/log
    emcmongoose/<IMAGE> \
    [<ARGS>]
```

### Debugging

The example below starts the Mongoose in the container with remote
debugging capability via the port #5005.

```bash
docker run \
    --network host \
    --expose 5005
    --entrypoint /opt/mongoose/entrypoint-debug.sh \
    emcmongoose/<IMAGE> \
    [<ARGS>]
```


# Kubernetes

Mongoose can be deployed on a [kubernetes](https://kubernetes.io/) cluster. Examples of yaml files are in the directory `/kubernetes`. The following describes a more detailed use of scripts:

> * You can use a ready-made cluster or [deploy your own](../KUBE-CLUSTER.md).
> * The examples use the mongoose image with the `latest` tag. To use specifically the version you need to specify ` - image: emcmongoose/mongoose:<x.y.z>`
> * All of the following configurations use `mongoose` namespace. Therefore, it is necessary to first create a namespace:
> ```bash
> kubectl create namespace mongoose
> ```

### Standalone

Run Mongoose in standalone mode:
```bash
kubectl apply -f kuberenetes/standalone.yaml 
```
CLI args can be added in following lines:
```yaml
...
args:
        - "-c"
        - /opt/mongoose/entrypoint.sh
          --load-step-limit-time=1m
          --storage-driver-type=dummy-mock
          <others CLI args>
```

#### Deployment & Pod

There are 2 options to start the mongoose: as `Pod` resource and as `Deployment` resource. In the first case, when the scenario completes, pod goes into status `Completed` before it is deleted. In the second case, after the completion deployment will be restarted infinitely many times.

Run Mongoose in standalone mode as deployment:
```bash
kubectl apply -f kuberenetes/standalone-deployment.yaml 
```

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

### Distributed Mode

Run Mongoose in distributed mode:
```bash
kubectl apply -f kuberenetes/distributed.yaml 
```

> `---` - separates configurations for different resources. Each resource can be launched separately as in the previous examples with command `kubectl apply ...`

The number of replicas means the number of nodes on which `Service` Mongoose will be running.
```
...
spec:
  replicas: 4
...
```

`Pod` plays the role of the entry node.

```
apiVersion: v1
kind: Pod
metadata:
  name: mongoose
  namespace: mongoose
...
   --load-step-node-addrs=mongoose-node-0.mongoose-node,mongoose-node-1.mongoose-node,mongoose-node-2.mongoose-node,mongoose-node-3.mongoose-node
...
```

With command `kubectl get -n mongoose pods` you can see information about running pods. In this example:

| NAME | READY | STATUS | RESTARTS | AGE |
| --- | --- | --- | --- | --- |
| mongoose-node-0   | 1/1     |Running   |0          |19m
| mongoose-node-1   | 1/1     |Running   |0          |19m
| mongoose-node-2   | 1/1     |Running   |0          |19m
| mongoose-node-3   | 1/1     |Running   |0          |19m

### REST API

Run Mongoose nodes:
```bash
kubectl apply -f kuberenetes/additional-node.yaml 
```
With command `kubectl get -n mongoose services` you can see inforamtion about running services. For this example:

|NAME            |TYPE           |CLUSTER-IP      |EXTERNAL-IP                   |PORT(S)          |AGE
| --- | --- | --- | --- | --- | ---
|mongoose-node   |LoadBalancer   |a.b.c.d   |**x.y.z.j**  |9999:31687/TCP   |25m

We are interested in external ip **x.y.z.j** . We can send HTTP-requests to it [(see Remote API)](doc/interfaces/api/remote). For example:
```
curl -v -X POST http://x.y.z.j:9999/run
```
