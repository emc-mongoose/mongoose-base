# Get started 

It's recommended to begin by exploring the 👉 **[basic terms](../design/architecture#1-basic-terms)** 👈 to better understand the documentation.

## Simple deploy

```
docker run --network host emcmongoose/mongoose-base
```

☝️ This command will launch mongoose in **standalone mode**, and the **storage mock** will be used as the tested storage.

> 📘 More about:
> * **[Modes](../design/modes)**
> * **[Storage mock/drivers](../design/architecture#21-storage-driver)**
> * **[Other ways to deploy](../deployment)**

## Simple load tuning

```
docker run --network host emcmongoose/mongoose-base \
    --load-op-limit-count=10000 \
    --item-data-size=1KB
```

☝️ This command will **"create"** 10000 "[items](../design/architecture#1-basic-terms)" on storage (in this case not real storage, but storage mock) and each "item" will have size "1KB".

```
docker run --network host emcmongoose/mongoose-base \
    --read \
    --item-input-path="some path to item on storage" \
    --load-step-limit-time=60s \
    --load-op-recycle=true
```

☝️ This command will perform **"read"** operation for 60 seconds. Mongoose will "read" all items from specified "item-input-path" (for example, for File System (as Storage) path = `/path/to/directory`) and when "items" run out mongoose will "read" them again (`recycle`).

> 📘 More about:
> * **[Items](../usage/item)**
> * **[CLI arguments](../usage/input/cli)** and **[configuartion options](../usage/input/configuration)**
> * **[Operation types](../usage/load/operations/types)**

## Simple scenario

Mongoose also supports input parameters using 'scenarios' - script files defining a workload.

Create `scenario1.js` file on local machine:
```
Load.run();
ReadLoad.run();
```
Run with mounted scenario:
```
docker run -d --network host  \
    -v $(pwd)/scenario1.js:/opt/scenario.js \
    emcmongoose/mongoose-base \
    --run-scenario=/opt/scenario.js
    --load-step-limit-time=20s
```

☝️ This command will **"create"** "items" with random size for 20 seconds and then will **"read"** "items" (not necessarily those created) for 20 seconds. The limit that was specified through the CLI applies to **all steps** within the scenario.


Create `scenario2.js` file on local machine:
```
Load.config({
	"storage": {
		"driver": {
			"limit": {
				"concurrency": 0					
				}
			}
		}
	})
	.run();

DeleteLoad.config({
		"load": {
			"step": {
				"limit": {
					"time": "5m"
					}
				}
			}
		})
		.run();
```
Run with mounted scenario:
```
docker run -d --network host  \
    -v $(pwd)/scenario2.js:/opt/scenario.js \
    emcmongoose/mongoose-base \
    --run-scenario=/opt/scenario.js
    --load-step-limit-time=20s
```

☝️ This command will **"create"** "items" with unlimited [concurrency]() level for 20 seconds (`--load-step-limit-time=20s`) and then will **"delete"** "items" (not necessarily those created) for 5 minutes (specified in scenario). The time limit that was set through the CLI argument applies **only to the first step**, since it did not have this parameter set.

> 📘 More about:
> * **[Item sizes](../usage/item/types#11-size)**
> * **[Scenarios](../usage/input/scenarios)**
> * **[Load steps and Load types](../usage/load/steps)** and **[Load steps in scenarios](../usage/input/scenarios#21-load-step)**

## Documentation

These examples describe only a small part of the functionality of the tool.

* More complex scenarios can be viewed in the [`/src/main/resources/example/scenario/js`](/src/main/resources/example/scenario/js) directory.

* Storage-specific options and examples can be found in the [SD driver repositories](https://github.com/emc-mongoose/mongoose#bundle-contents).

* A description of all components, options, design, etc. can be found in the [full documentation](/doc#documentation).
