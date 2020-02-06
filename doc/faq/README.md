# FAQ

# What is *latency* and *duration*?

Latency (actually response latency): this value is measured in nanoseconds and shows how much time passed since mongoose sent first byte to storage till first byte  from storage was returned. This value depends on object size in case of Create or Update, and doesn't in case of Read and Delete.
Duration: total response time (this value is measured in nanoseconds).

![](mongoose-lat-dur.png)

A = 
B = 
C = 
D = 

Latency = C-B
Duration = D-A
