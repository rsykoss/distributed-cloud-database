# Distributed Cloud Database
Use Java to build your own distributed and replicated cloud database system. Create a database system like Amazon's Dynamo.

# Architecture
This is extended from basic client server architecture. Each storage server (KVServers) are controlled and monitored by a central service called External Configuration Service (ECS). Every client can connect to any of the servers to request for key value pairs. 

<img src="https://user-images.githubusercontent.com/42865415/80793198-0f52e680-8bc9-11ea-85f2-dbe126c031df.png" height="314" width="490.5">

# Functions
#### 1. Basic Storage
This cloud database is able to store and retrieve key value pairs. When user try to retrieve keys that are not in storage, there will be suggestions of what the keys could be. Clients are able to use these commands. Type help to view these in detail.
```console
connect 127.0.0.1 5153  # Connect to server (to do any of below)
put <key> <val>         # puts key   
delete <key>            # deletes key       
get <key>               # get key (or partial key)
keyrange <key>          # returns which server contains key
keyrange_read <key>     # All server with replicated key as well
disconnect              # Disconnect from current server
quit                    # Disconnect and shut down cmd  
help                    # View all possible cmds                 
loglevel <level>        # Change Loglevel
createuser adminOrUser user pass # For admin to create user/admin
deleteuser user                  # For admin to del user/admin
```

#### 2. Cache Displacement Strategy
Cache size can be set using -c command line arg. Once the limit is passed, it will start to store the key value pairs into persistent data (textfile). KVservers can implement any of the 3 cache displacement strategies:
   1. FIFO (First In First Out)
   2. LFU (Least Frequently Used)
   3. LRU (Least Recently Used)

The cache displacement strategy can be set using -s followed by the strategies listed above. 
   

#### 3. Scalability
**Distributed** storage service was achieved through deployment of multiple servers. Administrators can deploy 1 server to many servers according to their needs. Each put request is assigned to a KV server via **consistent hashing**.

#### 4. Load Balanced
As the number of servers that are deployed increases, load will start to get unbalanced as storage is based on consistent hashing. Using virtual nodes, Load balancing is achieved.

In figure below, 3 servers were not evenly distributed initially, server A handling 2 tasks, server B handling 1 task and server C handling 3 tasks. However, with the introduction of virtual nodes (A*, B*, C*), each server handles 2 tasks, achieving a more balanced distribution of workload. This ensures that each server handles about equal number of key value pairs.

<img src="https://user-images.githubusercontent.com/42865415/80793202-137f0400-8bc9-11ea-985f-30747b0f213e.png" height="248.5" width="490.5">

#### 5. Replicated
Storage service starts replication when the 3rd server gets deployed. **Eventual consistency** is achieved by using an optimistic lazy Multi-Primary Replication using gossiping protocol.

#### 6. Authentication
 Authentication is also implemented based on the OAuth authentication framework, using JSON Web Token (JWT) as the access token. Default login details are admin/password. There are 2 kinds of user, one is admin and the other is user. Admin is able to create or delete admins or users. Clients are only able to connect to KV server once they are authenticated.

 <img src="https://user-images.githubusercontent.com/42865415/80793208-17128b00-8bc9-11ea-99bd-c97a696818ab.png" height="314.5" width="490.5">
 


# How to run (Server side)
This is an example to run it locally using local host 127.0.0.1. But other address can also be used.
#### Prerequisites:
Maven will be required. Install mvn from [here](https://maven.apache.org/download.cgi). Test that mvn is installed using `mvn -v`.

#### 1. Build project
Use maven to build project without running tests.
```console
mvn -Dmaven.test.skip=true package
```

#### 2. Start ECS
In the same cmd, run this and it should show that ECS has started.
```console
java -jar target/ecs-server.jar  -l ecs.log –ll ALL -a 127.0.0.1 -p 5152
````
#### 3. Start KVServer
Open up a new cmd and run this.
```console
java -jar target/kv-server.jar -l kvserver1.log -ll ALL -d data/kvstore1/ -a 127.0.0.1 -p 5153 -b 127.0.0.1:5152
```
Take note that -b address should be the same as ECS address. To start multiple KV Servers, open up another cmd and repeat this while changing the port number.


# Client side
#### 1. Open up client side
Open up another console which is going to be the client.
```console
java -jar target/echo-client.jar
```
At any point of time, `help` can be requested to know what are the syntax or possible arguments.

#### 2. Connect to an available kv server
Once the client is opened, connect to a server using:
```console
connect <address> <port>
```

#### 3. Login
Before client can be connected, client needs to login. There are two tiers: Admins and Users. Default login is as following:
```
Username: admin
PasswordL password
```
Since this default login is an admin, it is able to create or delete users. 

#### 4. Storage services
