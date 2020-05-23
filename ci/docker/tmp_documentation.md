```
MANAGER_NODE=10.199.197.119
NODES=(10.199.193.190 10.199.198.189)


docker swarm init
echo "user1" | docker secret create access_key -
#w03bo4zmrtc4xa4q01401u4n7
echo "secretKey1" | docker secret create secret_key -
#fb34x6o37gupzow7z5xbqt2yz
docker node update --label-add minio1=true node5
docker node update --label-add minio2=true node5
docker node update --label-add minio3=true node6
docker node update --label-add minio4=true node6
wget https://raw.githubusercontent.com/minio/minio/master/docs/orchestration/docker-swarm/docker-compose-secrets.yaml
docker stack deploy --compose-file=docker-compose-secrets.yaml minio_stack

10.199.193.190, 10.199.198.189
docker swarm join --token SWMTKN-1-0hmx7kt17mr4wim0k0i3yaq46cyj1la0szkkmly8bs3k52ls4y-c4m13ctp1b38tbqnyvp97gi9c 10.199.197.119:2377
```


```bash
docker run --name mongoose -d --network host -v mongoose:/opt/volume emcmongoose/mongoose:4.2.13 --storage-auth-uid=user1 --storage-auth-secret=secretKey1 --storage-net-node-addrs=10.199.193.190:9001,10.199.193.190:9002,10.199.198.189:9003,10.199.198.189:9004  --load-step-node-addrs=10.199.196.20,10.199.193.168,10.199.198.93  --run-scenario=/opt/volume/max.s3.4.nvme.10KB.js  --item-output-path=bucket1

docker run --name mongoose -d --network host -v mongoose:/opt/volume emcmongoose/mongoose:4.2.13 --storage-auth-uid=user1 --storage-auth-secret=secretKey1 --storage-net-node-addrs=10.199.197.119   --load-step-node-addrs=10.199.196.20,10.199.193.168,10.199.198.93  --run-scenario=/opt/volume/max.s3.4.nvme.10KB.js  --item-output-path=bucket1

docker run --name mongoose -d --network host -v mongoose:/opt/volume emcmongoose/mongoose:4.2.13 --storage-auth-uid=user1 --storage-auth-secret=secretKey1 --storage-net-node-addrs=10.199.197.119   --load-step-node-addrs=10.199.196.20,10.199.193.168,10.199.198.93  --run-scenario=/opt/volume/max.s3.4.nvme.10KB_READ.js  --item-output-path=bucket1

docker run -d --name mongoose1 -p 1091:1099 -p 9991:9999  emcmongoose/mongoose:4.2.13  --run-node
docker run -d --name mongoose2 -p 1092:1099 -p 9992:9999  emcmongoose/mongoose:4.2.13  --run-node
docker run -d --name mongoose3 -p 1093:1099 -p 9993:9999  emcmongoose/mongoose:4.2.13  --run-node

docker run -d --network host --name mongoose1 emcmongoose/mongoose:4.2.13  --run-node
docker run -d --network host --name mongoose2 emcmongoose/mongoose:4.2.13  --run-node
docker run -d --network host --name mongoose3 emcmongoose/mongoose:4.2.13  --run-node


10.199.197.119
docker swarm init
echo "user1" | docker secret create access_key -
#w03bo4zmrtc4xa4q01401u4n7
echo "secretKey1" | docker secret create secret_key -
#fb34x6o37gupzow7z5xbqt2yz
docker node update --label-add minio1=true node5
docker node update --label-add minio2=true node5
docker node update --label-add minio3=true node6
docker node update --label-add minio4=true node6
wget https://raw.githubusercontent.com/minio/minio/master/docs/orchestration/docker-swarm/docker-compose-secrets.yaml
docker stack deploy --compose-file=docker-compose-secrets.yaml minio_stack

10.199.193.190, 10.199.198.189
docker swarm join --token SWMTKN-1-0hmx7kt17mr4wim0k0i3yaq46cyj1la0szkkmly8bs3k52ls4y-c4m13ctp1b38tbqnyvp97gi9c 10.199.197.119:2377


```