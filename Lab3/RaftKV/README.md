> # 前言
>
> 源项目：[基于 Raft 共识算法的 K-V 数据库](https://github.com/leakey0626/Raft-KV)
>
> 建议完整读完分布式共识和Raft这两部分的知识再去看代码的实现，会有很大的帮助，关于RPC和kv数据库，如果不了解的人可以看下[了解RPC，简单的RPC框架](https://github.com/yeyuhl/SimpleRPC)和[了解NoSQL](https://cs186berkeley.net/notes/note17/)，Raft算法相关知识参考自唐伟志的《深入理解分布式系统》，简单的Raftkv实现，可以看[MIT6.824 lab3](https://github.com/OneSizeFitsQuorum/MIT6.824-2021/blob/master/docs/lab3.md)。

# 分布式共识

## 什么是分布式共识

Raft算法是一种分布式共识算法，注意“共识（Consensus）”不等于“一致性（Consistency）”。共识侧重于研究分布式系统中的节点达成共识的过程和算法，一致性则侧重于研究副本最终的稳定状态。

共识是什么呢？举个例子，A和B出去吃饭，A提议去吃麦当劳，B赞成。二者就“吃麦当劳”这件事上达成共识，这就是生活上的共识。而在分布式系统中，共识就是在一个可能出现任意故障的分布式系统中的多个节点（进程）对某个值达成共识。

假设一个分布式系统包含n个进程，记为｛0，1，2，3，...，n-1｝，每个进程都有一个初始值，进程之间互相通信。我们需要设计一种共识算法，使得尽管分布式系统中出现了故障，但是进程之间仍然能协商出某个不可撤回的最终决定值，且每次执行都满足以下三个性质：

- 终止性（Termination）：所有正确的进程最终都会认同某一个值。

- 协定性（Agreement）：所有正确的进程认同的值都是同一个值。

- 完整性/有效性（Integrity/Validity）：如果正确的进程都提议同一个值v，那么任何正确进程的最终决定值一定是v。


## 为什么要达成共识

那么为什么需要达成共识呢？想要回答这个问题，需要先从分布式系统底层问题讲起。首先，分布式系统有以下几个主要难题：网络不可靠问题、时钟不一致问题以及节点故障问题。想要解决这些问题，一种常规的方法就是**状态机复制（State Machine Replication，SMR）**。

所谓状态机，包括一组状态，一组输入，一组输出，一个转换函数，一个输出函数和一个独特的“初始”状态。一个状态机从“初始”状态开始，每个输入都被传入转换函数和输出函数，以生成一个新的状态和输出。在收到新的输入前，状态机的状态保持不变。状态机必须具备确定性：多个相同状态机的副本，从同样的“初始”状态开始，经历相同的输入序列后，会达到相同的状态，并输出相同的结果。

复制多个副本可以提供高可用和高性能的服务，但多副本又要考虑一致性问题，状态机的确定性就是实现容错和一致性的理想特性。假如某个节点的状态机输出了和其他节点不同的结果，可以推断这个节点出现故障并对其隔离和修复。

而状态机的复制常常是一个**多副本日志系统**：如果日志的内容和顺序都相同，多个进程从同一状态开始，并且从相同的位置以相同的顺序读取日志内容，那么这些进程将生成相同的输出，并且结束在相同的状态。

**共识算法常用来实现多副本日志**，共识算法使得每个副本对日志的值和顺序达成共识，每个节点都存储相同的日志副本，这样整个系统中的每个节点都能有一致的状态和输出。最终，这些节点看起来就像一个单独的、高可用的状态机。

**状态机复制**可以解决：

- 在网络延迟、分区、丢包、重复和重排序的情况下，不会返回错误的结果。

- 状态机不依赖于时钟。

- 高可用性。一般来说，只要集群中超过半数的节点正常运行，能够互相通信并且可以同客户端通信，那么这个集群就完全可用。例如，某些共识算法保证了由5个节点组成的分布式系统可以容忍其中的2个节点故障，有时甚至不需要系统管理员修复它，稍后故障节点会从持久化存储中恢复其状态，并重新加入集群。


此外，**达成共识**还可以解决分布式系统中的一些经典问题：

- 互斥（MumalExcluSion）：分布式系统中哪个进程先进入临界区访问资源?如何实现分布式锁？

- 选主（LeaderEleCtion）：对于单主复制的数据库，想要正确处理故障切换，需要所有节点就哪个节点是领导者达成共识。如果某个节点由于网络故障而无法与其他节点通信，则可能导致系统中产生两个领导者，它们都会处理写请求，数据就可能产生分歧，从而导致数据不一致或丢失。

- 原子提交（AtomicCommit）：对于跨多节点或跨多分区事务的数据库，一个事务可能在某些节点上失败，但在其他节点上成功。如果我们想要维护这种事务的原子性，则必须让所有节点对事务的结果都达成共识：要么全部提交，要么全部中止或回滚。


总而言之，共识可以让分布式系统向单一节点一样工作，并且具备高可用性、自动容错和高性能。

最后需要提及的是，一个分布式共识算法需要具备两个属性：**安全性（Safety）** 和 **活性（Liveness）**。安全性意味着所有正确的进程都认同同一个值，活性意味着分布式系统最终会认同某一个值。我们可以认为安全性和活性是从终止性、协定性和完整性中提炼出来的更常用的属性。

# Raft

在Raft之前，其实已经有了一系列Paxos的算法。但Paxos难以理解，并且难以在生产环境中实现。因此Raft诞生了，与所有分布式共识算法的目标一样，Raft算法也是用来保证日志完全相同地复制到多台服务器上，以实现状态机复制的算法。

Raft算法所运行的**系统模型**为：

- 服务器可能宕机、停止运行，过段时间再恢复，但不存在非拜占庭故障，即节点的行为是非恶意的，不会篡改数据。

- 消息可能丢失、延迟、乱序或重复；可能有网络分区，并在—段时间之后恢复。


Raft算法和Multi-Paxos算法—样是基于 **领导者（Leader）** 的共识算法，因此，Raft算法主要讨论两种情况下的算法流程：领导者正常运行；或者领导者故障，必须选出新的领导者接管和推进算法。

使用基于领导者的算法的优势是，只有—个领导者，逻辑简单。但难点是，当领导者发生改变时，系统可能处于不一致的状态，状态机日志也会堆积一些没有批准的日志，因此，当下一任领导者上任时，必须对状态机日志进行清理。

我们将从以下几部分分析Raft算法：
（1）领导者选举。在Multi-Paxos一书中作者提到，简单地使用服务器id选举领导者虽然容易实现，但也存在日志落后的问题。Raft算法展示了一个选出最优领导者的算法。

（2）算法正常运行。也是算法最简单的部分，描述如何实现日志复制。
（3）领导者变更时的安全性和一致性。算法中最棘手和最关键的部分，描述新选举出来的领导者要做的日志清理工作。
（4）处理旧领导者。万一旧的领导者并没有真的下线怎么办？该如何处理系统中存在两个领导者的情况？
（5）客户端交互。集群如何与客户端交互？
（6）配置变更。如何在集群中增加或删除节点？
（7）日志压缩。为了减少磁盘存储空间，同时让新节点快速跟上系统状态，Raft算法还会压缩日志生成快照。
（8）实现线性一致性。如何通过Raft算法实现线性一致性读？

## 基本概念

Raft算法中的服务器在任意时间只能处于以下三种状态之一：

- 领导者（Leader）。领导者负责处理所有客户端请求和日志复制。同一时刻最多只能有一个正常工作的领导者。

- 跟随者（Follower）。跟随者完全被动地处理请求，即跟随者不主动发送RPC请求，只响应收到的RPC请求，服务器在大多数情况下处于此状态。

- 候选者（Candidate）。候选者用来选举出新的领导者，候选者是处于领导者和跟随者之间的暂时状态。


Raft算法选出领导者意味着进入一个新的**任期（Term）**，实际上任期就是一个逻辑时间。Raft算法将分布式系统中的时间划分成一个个不同的任期来解决之前提到的时序问题。每个任期都由一个数字来表示任期号，任期号在算法启动时的初始值为0，单调递增并且永远不会重复。

一个正常的任期至少有一个领导者，任期通常分为两部分：任期开始时的选举过程和任期正常运行的部分，如下图所示：

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/1.png?raw=true)

有些任期内可能没有选出领导者，如上图的Trem3，这时会立即进入下一个任期，再次尝试选出一个领导者。

每台服务器需要维护一个currentTerm变量，表示服务器当前已知的最新任期号。变量currentTerm必须持久化存储，以便在服务器宕机重启时能够知道最新任期。

任期对于Raft算法来说非常重要。任期能够帮助Raft识别过期的信息。例如，如果currentTerm＝2的服务器与currentTerm＝3的服务器通信，那么我们可以知道第一个节点上的信息是过期的。Raft算法只使用最新任期的信息。后面我们会遇到各种情况，需要检测和消除不是最新任期的信息。

最后Raft算法中服务器之间的通信主要通过两个RPC调用实现，一个是**Requestvote RPC**， 用于领导者选举；另一个是**AppendEntries RPC**，被领导者用来复制日志和发送心跳。

## 领导者选举

Raft算法启动的第一步就是要选举出领导者。每个节点在启动时都是跟随者状态，跟随者只能被动地接收领导者或候选者的RPC请求。所以，如果领导者想要保持权威，则必须向集群中的其他节点周期性地发送心跳包，即空的AppendEntries消息。如果一个跟随者节点在选举超时时间（用变量electionTimeout表示，一般在100ms至500ms的范围内）内没有收到任何任期更大的RPC请求，则该节点认为集群中没有领导者，于是开始新的一轮选举。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/2.png?raw=true)

当一个节点开始竞选时，其**选举流程**如下图所示。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/3.png?raw=true)

第一步，节点转为候选者状态，其目标是获取超过半数节点的选票，让自己成为新一任期的领导者。

第二步，增加自己的当前任期变量currentTerm，表示进入一个新的任期。

第三步，先给自己投一票。

第四步，并行地向系统中的其他节点发送RequestVote消息索要选票，如果没有收到指定节点的响应，则节点会反复尝试，直到发生以下三种情况之一才更新自己的状态。

- 获得超过半数的选票。该节点成为领导者，然后每隔一段时间向其他节点发送AppendEntries消息作为心跳，以维持自己的领导者身份。

- 收到来自领导者的AppendEntries心跳，说明系统中已经存在一个领导者了，节点转为跟随者。

- 经过选举超时时间后，其他两种情况都没发生，也没有节点能够获胜节点开始新一轮选举，回到上图中的第二步。


选举过程中需要保证共识算法的两个特性：安全性和活性。安全性是指一个任期内只会有一个领导者被选举出来。需要保证：

- 每个节点在同一任期内只能投一次票，它将投给第一个满足条件的RequestVote请求，然后拒绝其他候选者的请求。这需要每个节点新增一个投票信息变量votedFor，表示当前任期内选票投给了哪个候选者，如果没有投票，则votedFor为空。投票信息votedFor也要持久化存储，以便节点宕机重启后恢复投票信息，否则节点重启后votedFor信息丢失，会导致一个节点投票给不同的候选者。

- 只有获得超过半数节点的选票才能成为领导者，也就是说，两个不同的候选者无法在同一任期内都获得超过半数节点的选票。


而活性意味着要确保系统最终能选出一个领导者，如果系统始终选不出一个领导者，那么就违反了活性。

问题是，原则上**节点可以无限重复分割选票**。假如选举同一时间开始，然后瓜分选票，没有达成任何多数派，又同一时间超时，同一时间再次选举，如此循环。类似于Paxos的活锁问题。那么，同样可以用解决活锁的办法来解决，即节点随机选择超时时间，选举超时时间通常在[ T , 2T ]区间之内（例如150～300ms）。由于随机性节点不太可能再同时开始竞选，所以先竞选的节点有足够的时间来索要其他节点的选票。如果T远远大于网络广播时间，那么效果更佳。

## 日志复制

在Raft算法中，每个节点存储自己的日志副本（用变量log[]表示），日志中的每个日志条目（LogEntIy）包含如下内容：

- **索引（Index）**。索引表示该日志条目在整个日志中的位置。

- **任期号**。日志条目首次被领导者创建时的任期。

- **命令**。应用于状态机的命令。


**Raft算法通过索引和任期号唯一标识一条日志记录**。并且日志必须持久化存储，一个节点必须先将日志条目安全写到磁盘中，才能向系统中其他节点发送请求或回复请求。**如果一条日志条目被存储在超过半数的节点上，则认为该记录已提交（committed）**——这是Raft算法非常重要的特性！如果一条记录已提交，则意味着状态机可以安全地执行该记录，这条记录就不能再改变了。如下图中，第一条至第七条日志已经提交，而第八条日志尚未提交。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/4.png?raw=true)

Raft算法通过AppendEntries消息来复制日志，和心跳消息共用同一个RPC，不过AppendEntries消息用来发送心跳消息时不包含日志信息。当Raft算法正常运行时，**日志复制的流程**为：

1. 客户端向领导者发送命令，希望该命令被所有状态机执行。

2. 领导者先将该命令追加到自己的日志中，确保日志持久化存储。

3. 领导者并行地向其他节点发送AppendEntries消息，等待响应。

4. 如果收到超过半数节点的响应，则认为新的日志记录已提交。接着领导者将命令应用（apply）到自己的状态机，然后向客户端返回响应。此外，一旦领导者提交了一个日志记录， 将在后续的AppendEntries消息中通过LeaderCommit参数通知跟随者，该参数代表领导者己提交的最大的日志索引，跟随者也将提交日志索引小于LeaderCommit的日志，并将日志中的命令应用到自己的状态机。

5. 如果跟随者宕机或请求超时，日志没有成功复制，那么领导者将反复尝试发送AppendEntries消息。

6. 性能优化：领导者不必等待每个跟随者做出响应，只需要超过半数跟随者成功响应（确保日志记录已经存储在超过半数的节点上）就可以回复客户端了。这样保证了即使有—个很慢的或发生故障的节点也不会成为系统瓶颈，因为领导者不必等待很慢的节点。


Raft算法的日志通过索引和任期号唯一标识一个日志条目，为了保证安全性，
Raft算法维持了以下两个特性：

- 如果两个节点的日志在相同的索引位置上的任期号相同，则认为它们具有一样的命令，并且从日志开头到这个索引位置之间的日志也完全相同。

- 如果给定的记录已提交，那么所有前面的记录也己提交。Paxos算法允许日志不连续地提交，但Raft算法的日志必须连续地提交，不允许出现日志空洞。


除此之外，为了维护这两个特性，Raft算法尝试在集群中保持日志较高的一致性。Raft算法通过AppendEntries消息来检测之前的一个日志条目：每个AppendEntries消息请求包含新日志条目之前一个日志条目的索引（记为prevLogIndex）和任期（记为preLogTerm）；跟随者收到请求后，会检查自己最后一条日志的索引和任期号是否与请求消息中的prevLogIndex和preLogTerm相匹配，如果匹配则接收该记录，不匹配则拒绝。这个流程成为**一致性检查**，如下图所示：

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/5.png?raw=true)

## 领导者更替

当**新的领导者上任后，日志可能就会变脏**，因为前一任领导者可能在完成日志复制之前就宕机了。Raft算法**在新领导者上任时，对于冲突的日志不会采取任何特殊处理**。也就是说，当新领导者上任后，它不会立即执行任何清理操作，它会在正常运行期间执行清理操作，而不是专门为此做任何额外的工作。

这样处理的原因是，一个新的领导者刚上任时，往往意味着有机器发生故障了或者发生了网络分区，此时并没有办法立即清理它们的日志，因为此时可能仍然无法连通这些机器。在机器恢复运行之前，我们必须保证系统正常运行。这样做的前提是，Raft算法假定了领导者的日志始终是对的，所以领导者要做的是，随着时间的推移，让所有跟随者的日志最终都与其匹配。

但与此同时，领导者也可能在完成日志复制这项工作之前又出现故障，没有复制也没有提交的日志会在一段时间内堆积起来，从而造成看起来相当混乱的情况。如下图所示，从索引为4的日志之后，系统中的日志开始变得混乱不堪：

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/6.png?raw=true)

上图中，S4和S5是任期2、3、4的领导者，但不知何故，它们没有复制自己的日志记录就崩溃了，系统分区了一段时间，S1、S2、S3轮流成为任期5、6、7的领导者，但无法与S4、S5通信以执行日志清理，造成了图中的局面，导致我们看到的日志非常混乱。

图中唯一重要的是，索引1到3之间的日志条目可以确定是己提交的，因为它们已存在于多数派节点，因此我们必须确保保留下它们。而其他日志都是未提交的，我们还没有将这些命令传递给状态机，也没有客户端会收到这些执行的结果，所以不管是保留还是丢弃它们都无关紧要。

前面提到，一旦状态机应用了一条日志中的命令，就必须确保其他状态机在同样索引的位置不会执行不同的命令，否则就违反了状态机复制的一致性。 Raft算法的安全性就是为了**保证状态机复制执行相同的命令**。安全性要求，如果某个日志条目在某个任期号己提交，那么这条记录必然出现在更大任期号的未来领导者的日志中。也就是说，无论未来领导者如何变更，已提交的日志都必须保留在这些领导者的日志中。 这保证了状态机命令的安全性，意味着：第一，领导者不会覆盖日志中已提交的记录。第二，只有领导者的日志条目才能被提交，并且在应用到状态机之前，日志必须先被提交。

这决定了我们要修改选举程序，检查的内容如下：
（1）如果节点的日志中没有正确提交的日志，则需要避免使其成为领导者。
（2）稍微修改提交日志的逻辑。前面的定义是一个日志条目在多数派节点中存储即是己提交的日志，但在某些时候，假如领导者刚复制到多数派就宕机了，则后续领导者必须延迟提交日志记录，直到我们知道这条记录是安全的——所谓安全的，就是后续领导者也会有这条日志。

这一优化称为**延迟提交**，延迟提交的前提是要先修改选举程序，目的是选出最佳领导者。我们如何确保选出了一个很好地保存了所有已提交日志的领导者呢?

举个例子：在下图的系统中选出一个新领导者，但是此时第三台服务器不可用。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/7.png?raw=true)

在第三台服务器不可用的情况下，仅看前两个节点的日志我们无法确认索引为5的日志是否达成多数派，故无法确认第5条日志是否已提交。Raft算法通过比较日志，在选举期间，选择最有可能包含所有已提交日志的节点作为领导者。所谓最有可能，就是找出日志最新并且最完整的节点来作为领导者，具体流程为：

1. 候选者C在RequestVote消息中包含自己日志中的最后一条日志条目的索引和任期，记为lastIndex和lastTerm。

2. 收到此投票请求的服务器V将比较谁的日志更完整，如果服务器V的任期比候选者C的任期新，或者两者任期相同但服务器V上的日志比服务器C上的日志更完整，那么V将拒绝投票。该条件用伪代码表示为：

``(lastTermV > lastTermC) || (lastTermV == lastTermC) && (lastIndexV > lastIndexC)``


无论是谁赢得选举，都可以确保领导者在超过半数投票给它的节点中拥有最完整的日志，意思就是选出来的领导者的日志的索引和任期这对唯一标识是最大的，当然，这对标识中任期的优先级又更高一些，所以先判断任期是否更新。

Raft算法的选举限制保证选出来的领导者的日志任期最新，日志长度也最完整，这样能够避免领导者去追赶其他节点的日志而造成系统阻塞。可见Raft算法中领导者非常权威。

## 选举限制举例

举例说明新的选举规则。

第一种情况是，领导者决定提交日志。

如下图所示，任期为2的领导者s1的第4条日志刚刚被复制到服务器S3，并且领导者可以看到第4条日志条目已复制到超过半数的服务器，那么该日志可以提交，并且安全地应用到状态机。现在，这条记录是安全的，下一任期的领导者必会包含此记录，因此，如果此时重新发起选举，那么服务器S4和S5都不可能从其他节点那里获得选票，因为S5的任期太旧，S4的日志太短。只有前三台中的一台可以成为新的领导者，服务器S1当然可以，服务器S2和S3也可以通过获取S4和S5的选票成为领导者。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/8.png?raw=true)

第二种情况是，领导者试图提交之前任期的日志。

如下图所示的情况，任期为2的日志条目一开始仅写在服务器S1和S2两个节点上，由于网络分区的原因，任期3的领导者S5并不知道这些记录，领导者S5创建了自己任期的三条记录后还没来得及复制就宕机了。之后任期4的领导者S1被选出，领导者S1试图与其他服务器的日志进行匹配，因此它复制了任期2的日志到S3。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/9.png?raw=true)

虽然此时索引为3、任期为2的这条日志已经复制到多数派节点，但这条日志是不安全的，不能提交。这是因为领导者S1可能在提交后立即宕机，然后服务器S5发起选举，由于服务器S5的日志比服务器S2、S3和S4都要新且长，所以服务器S5可以从服务器S2、S3和S4处获得选票，成为任期5的领导者。一旦S5成为新的领导者，那么它将复制自己的第3条到第5条日志条目，这会覆盖服务器S2和S3上的日志，服务器S2和S3上的第3条日志条目将消失——这不符合已提交日志不能被修改的要求。

第二种情况说明了**日志仅仅是复制到多数派，Raft算法也并不能立即认为日志可以提交，并应用到状态机**。之后某个节点可能覆盖这些日志，重新执行某些命令，这是违反状态机安全性的。这就是为什么Raft算法要延迟提交的原因。

我们需要一条新规则来解决这个问题。

## 延迟提交之前任期的日志条目

新的选举依然不足以保证日志安全，我们还需要继续修改提交规则。Raft算法要求领导者想要提交一条日志，必须满足：
（1）日志必须存储在超过半数的节点上。
（2）领导者必须看到超过半数的节点上还存储着至少一条自己任期内的日志。
如下图所示，回到上一节的第二种情况，索引为3且任期为2的日志条目被复制到服务器S1，S2和S3时，虽然此时多数派已经达成，但Raft算法仍然不能提交该记录，必须等到当前任期也就是任期4的日志条目也存储在超过半数的节点上，此时第3条和第4条日志才可以被认为是已提交的。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/10.png?raw=true)

此时服务器S5便无法赢得选举了，因为它无法从服务器S1、S2和S3处获得选票，无法获得多数派节点的选票。 结合新的选举规则和延迟提交规则，我们可以保证Raft的安全性。但实际上该问题并没有彻底解决，还有一点点瑕疵。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/11.png?raw=true)

如上图所示，如果先按错误的情况，也就是领导者可以提交之前任期的日志，那么上述的流程如下:
（1）服务器S1是任期2的领导者（S1的日志有个黑框）, 日志己经复制到了跟随者S2。

（2）领导者S1宕机，服务器S5获得S3、S4和S5的选票成为领导者，然后写了一条索引为2且任期为3的日志。

（3）领导者S5刚写完就又宕机了，服务器S1重新当选领导者，当前任期即currentTerm等于4。此刻还没有新的客户端请求进来，领导者S1将索引为2且任期为2的日志复制到了S3，多数派达成，领导者S1提交了这个日志（注意，任期号2不是当前任期的日志，我们是在描述错误的情况）。然后客户端请求进来，刚写了一条索引为4且任期为4的日志，领导者S1就又发生故障了。

（4）这时服务器S5可以通过来自S2、S3、S4和自己的投票重新成为领导者，将索引为2且任期为3的日志复制到其他所有节点并提交，此时索引为2这个位置的日志提交了两次，一次任期为2，另一次任期为3。对于状态机来说，这是绝对不允许发生的，因为已经提交的日志不能够被覆盖!

（5）这里的情况是，领导者S1在崩溃之前将自己任期为4的日志复制到了大多数机器上，这样服务器S5就不可能选举成功，自然就不会重复提交。

这里主要通过（4）来说明问题所在。所以我们要增加提交的约束，不让（4）这种情况发生。这个约束就是，**领导者只能提交自己任期的日志，从而间接提交之前任期的日志**。如果加了约束，那么会变成什么样呢？前面（1）和（2）没有任何改变，我们从（3）这一步开始分析。

在（3）中还是将索引为2且任期为2的日志复制到大多数，由于领导者当前任期即currentTerm等于4，日志中的任期不属于领导者的任期，所以不能提交这条日志。如果领导者S1将任期为4的日志复制到多数派，那么领导者就可以提交日志了，此时索引为2且任期为2的日志也跟着一起间接被提交，其实这就是（5）中的情况，任期为1、2、4的日志都被提交了。

上述（4）的情况是理解问题的关键。如果领导者S1只将任期为4的日志写入自己本地，然后就宕机了，那么服务器S5通过S2、S3和S4的选票选举成功成为领导者，然后将索引为2且任期为3的日志复制到所有节点，现在第2条日志是没有提交过的。此时（4）中的领导者S5已经将索引为2且任期为3的日志复制到所有节点，但它不能提交此日志条目。因为领导者S5在前任领导者S1（任期为4）选举出来后，其任期至少是5，如果选举超时或冲突，任期甚至可能是6、7、 8...我们假设S5的任期就是5。但第2条日志的任期很明显是3，因为约束领导者不能提交之前任期的日志，所以这条日志是不能提交的。只有等到新的请求进来，超过半数节点复制了任期为5的日志后，任期为3的日志才能跟着一起提交，如下图所示。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/12.png?raw=true)

这就产生一个问题，虽然增加了延迟提交的约束系统不会重复提交了，但如果一直没有新的客户端请求进来，那么索引为2且任期为3的日志岂不是就一直不能提交？系统不就阻塞了吗？如果这里的状态机实现的是一个分布式存储系统，那么问题就很明显了。假设（4）中第2条日志里的命令是Set("k","1")，那么服务器S5当选领导者后，客户端使用Get("k")查询k的值，领导者查到该日志有记录且满足多数派，但又不能回复1给客户端，因为按照约束这条日志还未提交，线性一致性要求不能返回旧的数据，领导者迫切地需要知道这条日志到底能不能提交。

于是，为了这个解决问题，Raft论文提到了引入一种**no-op空日志**。no-op空日志即只有索引和任期信息，命令信息为空，这类日志不会改变状态机的状态和输出，只是用来保持领导者的权威，驱动算法运行。

具体流程是，领导者刚选举成功的时候，不管是否有客户端请求，立即向自己本地追加一条no-op空日志，并立即将no-op空日志复制到其他节点。no-op空日志属于领导者任期的日志，多数派达成后立即提交，no-op空日志前面的那些之前任期未提交的日志全部间接提交了，问题也就解决了。比如上面的分布式数据库，有了no-op空日志之后，领导者就能快速响应客户端查询了。

本质上，no-op空日志可以使领导快速提交之前任期未提交的日志，确认当前commitIndex参数，即领导者已知的已提交的最高日志索引。这样系统才会快速对外正常工作。

## 清理不一致日志

领导者变更可能导致日志的不一致，之前主要展示了会影响状态机安全的情况，这里展示另外可能出现的两种情况，如下图所示。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/13.png?raw=true)

从上图可以看出，系统中通常有两种不一致的日志：**缺失的条目**（Missing Entries）和**多出来的日志条目**（Extraneous Entries）。新的领导者必须使跟随者的日志与自己的日志保持一致，我们要清理的就是这两种日志。对于缺失的条目，Raft算法会发送AppendEntries消息来补齐；对于多出来的条目，Raft算法会想办法删除。

为了清理不一致的日志，领导者会为每个跟随者保存变量nextIndex[]，用来表示要发送给该跟随者的下一个日志条目的索引。对于跟随者i来说，领导者上的nextIndex[i]的初始值为**1＋领导者最后一条日志的索引**。领导者还可以通过nextIndex[]来修复日志。如果AppendEntries消息发现日志一致性检查失败，那么领导者递减对应跟随者的nextIndex[i]值并重试。具体流程如下图所示。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/14.png?raw=true)

对于跟随者1，此时属于缺失条目，其完整流程为：
（1）一开始领导者根据自己的日志长度，记nextIndex[1]的值为11，带上前一个日志条目的唯一标识：索引为10且任期为6。检查发现跟随者1索引为10处没有日志，检查失败。

（2）领导者递减nextIndex[1]的值，即nextIndex[1]等于10，带上前一个日志条目的唯一标识：索引为9且任期为6。跟随者1处还是没有日志，依然检查失败。

（3）如此反复，直到领导者的nextIndex[1]等于5时，带上索引为4且任期为4的信息，该日志在跟随者1上匹配。接着领导者会发送日志，将跟随者1从5到10位置的日志补齐。对于跟随者2，此时属于多出来的日志，领导者同样会从nextIndex[2]为11处开始检查，一直检查到nextIndex[2]等于4时日志才匹配。值得注意的是，对于这种情况，跟随者覆盖不一致的日志时，它将删除所有后续的日志记录，Raft算法认为任何无关紧要的记录之后的记录也都是无关紧要的，如下图所示。之后再由领导者发送日志来补齐。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/15.png?raw=true)

## 处理旧领导者

旧的领导者有时并不会马上消失，例如，网络分区将领导者与集群的其余节点分隔，其余节点选举出了一个新的领导者，两个领导者在网络分区的两端各自工作。问题在于，如果网络分区恢复，旧的领导者重新连接，它并不知道新的领导者已经被选出来，它会尝试作为领导者继续复制和提交日志。此时如果正好有客户端向旧的领导者发送请求，那么旧的领导者会尝试存储该命令并向其他节点复制日志——我们必须阻止这种情况的发生。

任期就是用来发现过期领导者或候选者的，其处理逻辑为：

- 每个RPC请求都包含发送方的任期。

- 如果接收方发现发送方的任期陈旧，那么无论哪个过程，该RPC请求都会被拒绝。接收方将已知的最新任期回传给发送方，发送方知道自己任期已经过期后，转变到跟随者状态并更新其任期。

- 如果接收方发现自己的任期陈旧，那么接收方将自己转为跟随者，更新自己的任期，然后正常地处理RPC请求。


由于新领导者的选举会更新超过半数服务器的任期，因此旧的领导者即便收到客户端请求也不能提交新的日志，因为它会联系至少一台具有新任期的多数派集群的节点，并发现自己任期太旧，然后自己转为跟随者继续工作。

## 客户端协议

Raft算法要求必须由领导者来处理客户端请求，如果客户端不知道领导者是谁，那么它会先和任意一台服务器通信；如果通信的节点不是领导者，那么它会告诉客户端领导者是谁，领导者将命令写入本地并复制、提交和执行到状态机之后，才会做出响应。

如果领导者收到请求后宕机，则会导致请求超时，客户端会重新发出请求到其他服务器上，最终又重定向到新的领导者，用新的领导者处理请求，直到命令被执行。

这里有一个问题和Multi-Paxos类似，就是一个命令可能被执行两次的风险——领导者可能在执行命令之后在响应客户端之前宕机，此时客户端再去寻找下一个领导者处理请求，同一个命令就会被状态机执行两次，这是不可接受的。

解决办法是依然让客户端发送给领导者的每个请求都附加上一个唯一id，领导者将唯一id写到日志记录中。在领导者接受请求之前，先检查其日志中是否已经具有该请求的id。如果请求id已经在日志中，则说明这是重复的请求，此时忽略新的命令，直接返回已执行的命令的响应。每个命令只会被执行一次，这是实现线性一致性的关键要素。这部分逻辑Raft算法和Multi-Paxos算法还是非常相似的。

## 实现线性一致性

至此，我们仍然没有实现线性一致性。例如，系统发生网络分区时，可能存在两个领导者。旧领导者仍然在工作，但集群的其他部分已经选举出一个新的领导者，并提交了新的日志条目。如果此时客户端联系的领导者恰好是旧领导者，那么旧领导仍然会认为自己是权威的，它将直接返回旧的结果给客户端，这显然不满足线性一致性。线性一致性要求读操作必须返回最近一次提交的写操作的结果。

一种实现线性一致性读的方法是，领导者将读请求当作写请求进行处理，即领导者收到读请求时同样写入一条日志，等到Raft集群将该日志复制、提交并应用到状态机后，领导者就能确认自己确实是集群的真正领导者（旧领导者无法提交日志），然后向客户端返回读请求的结果。

这种方法存在的问题是，每次读请求都要完整地运行一次Raft实例，尤其是需要复制日志并将日志写入多数派节点的持久化存储，这将造成额外的性能开销。

Raft算法可以通过在领导者增加一个变量readIndex来优化一致性读的实现，其主要流程为：

（1）如果领导者当前任期内还没有已提交的日志条目，它就会等待，直到有已提交日志。之前提到，领导者完整性保证了领导者必须拥有所有已提交的日志条目，但在其任期刚开始时，领导者可能不知道哪些是已提交的。为了了解哪些日志已提交，它需要在其任期刚开始时提交一个no-op空日志。一旦no-op空日志条目被提交，领导者的commitIndex至少和其任期内的其他服务器一样大。

（2）领导者将其当前的commitIndex的值赋值给readIndex。

（3）领导者收到读请求后，需要确认它是集群真正的领导者。领导者向所有跟随者发送新一轮的心跳如果收到多数派的响应，那么领导者就知道，在它发出心跳那一刻，不可能有—个任期更大的领导者存在。领导者还知道，此时readIndex（即commitIndex）是集群中所有节点已知的最大的已提交索引。

（4）领导者等待它的状态机应用日志中的命令，至少执行到日志索引等于readIndex（即commitIndex）处的日志。

（5）领导者执行读请求，查询其状态，并将结果返回给客户端。

实际上，上述方式仍然是保证领导者在当前任期提交过日志，确保领导者是真正的领导者。这种方式比将只读查询作为新日志条目提交到日志中更有效率，避免了对每个读请求都要写入磁盘和复制日志的开销。

现在，读请求仍然要由领导者处理。为了提高系统的读操作吞吐量，可以让跟随者帮助领导者处理读请求，转移领导者的负载，使领导者能够处理更多的写请求。但是，如果没有额外的措施那么跟随者处理读请求也会有返回旧数据的风险。例如，一个跟随者可能很长时间没有收到领导者的心跳或者跟随者收到的是旧领导者的日志。为了跟随者安全地处理读请求，跟随者会向领导者发送请求询问最新的readIndex。领导者执行一遍上述流程的第1步到第3步，得知最新的commitIndex和readIndex；跟随者执行第4步到第5步，将提交日志应用到状态机，之后跟随者便可以处理读请求。

虽然上面的方法使系统满足线性一致性的要求，但也增加了网络消息轮次。一种优化方式是，领导者使用正常心跳机制来维护一个租约，心跳开始时间记为Start，—旦领导者的心跳被多数派确认，那么它将延长其租约时间到start＋electionTimeout/clockDriftBound时间。在租约时间内，领导者可以安全地回复只读查询，而不需要额外发送消息。

之所以可以这样优化，是因为Raft选举算法保证一个新的领导者至少在选举超时时间electionTimeout后才被选出，所以在租约时间内，可以认为不会发生选举，领导者仍然是真正的领导者。

租约机制假设了一个时钟漂移的界限clockDriftBound，即在这一段时间内，服务器的时间不会突然漂移超过这个界限（这里可以简单理解为由于时钟同步等行为，服务器上的时间突然改变）。如果服务器时钟走时违反了时钟漂移界限，则系统还是可能返回陈旧的消息。Raft论文中提到，除非是为了满足性能要求，否则不建议使用租约这种替代方式。

## 配置变更

随着时间的推移，系统管理员需要替换发生故障的机器或者修改集群的节点数量，需要通过一些机制来变更系统配置，并且是安全、自动的方式，无须停止系统的运行。通常系统配置是由每台服务器的id和地址组成的。系统配置信息对于共识算法是非常重要的，它决定了多数派的组成。

首先我们要意识到，不能直接从旧配置切换到新配置，这可能导致出现矛盾的多数派。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/16.png?raw=true)

如上图，系统本来以三台服务器的配置运行，此时系统管理员要添加两台服务器。如果系统管理员直接修改配置，那么集群中的节点可能无法完全在同一时间做到配置切换，这会导致服务器S1和S2形成旧集群的多数派，而同一时间服务器S3、S4和S5己经切换到新配置，这会产生两个不同的多数派。

这说明我们必须使用一个**两阶段（two-phase）协议**。Raft算法的作者Diego Ongaro曾说过：“如果有人告诉你，他可以在分布式系统中一个阶段就做出决策，你应该非常认真地询问他，因为他要么错了，要么发现了世界上所有人都不知道的东西。”

Raft算法通过联合共识（Joint Consensus）来完成两阶段协议，即让新、旧两种配置都获得多数派选票。如下图所示，在**第一阶段**，领导者收到$C_{new}$的配置变更请求后，先写入一条$C_{old＋new}$的日志，配置变更立即生效。然后领导者将日志通过AppendEntries消息复制到跟随者上，收到$C_{old+new}$日志的节点立即应用该配置作为当前节点的配置；当$C_{old+new}$日志被复制到多数派节点上时，$C_{old+new}$的日志就被领导者提交。$C_{old+new}$日志已提交保证了后续任何领导者一定保存了$C_{old+new}$日志，领导者选举过程必须获得旧配置中的多数派和新配置中的多数派同时投票。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/17.png?raw=true)

$C_{old+new}$日志提交后，进入**第二阶段**，领导者立即写入一条$C_{new}$的日志，并将该日志通过AppendEntries消息复制到跟随者上，收到$C_{new}$日志的跟随者立即应用该配置作为当前节点的配置。$C_{new}$日志复制到多数派节点上时，$C_{new}$日志被领导者提交。在$C_{new}$日志提交以后，后续的配置就都基于$C_{new}$了。

联合共识还有一个值得注意的细节，配置变更过程中，来自新旧配置的节点都有可能成为领导者，如果当前领导者不在$C_{new}$配置中，一旦$C_{new}$提交，则它必须下台（step down）。如下图所示，旧领导者不再是新配置的成员后，还有可能继续服务一小段时间，即旧领导者可能在$C_{new}$配置下继续当领导者，直到$C_{new}$的日志复制到多数派上并提交后，旧领导者就
要下台。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/18.png?raw=true)

如果没有额外的机制，那么配置变更可能会扰乱集群。举个例子，如果领导者创建了$C_{new}$日志条目，那么不在$C_{new}$中的跟随者将不再收到心跳，如果该服务器没有关机或杀死进程，那么该跟随者会超时并开始新的选举。此外，该跟随者并不会收到$C_{new}$日志条目，它不知道自己已经被移除，它将带有新任期号的RequestVote消息发送给新集群中的领导者（旧领导者知道新领导者的地址），这导致当前领导者转为跟随者状态，虽然旧领导者因为日志不完整无法选举成功，但也会影响新的领导者重新选举。这个过程可能一直重复，导致系统可用性变差，如果同时移除多个节点，那么情况可能会更糟糕。

这个问题的一种解决方式是加入一个Pre-Vote阶段，在任何节点发起选举之前，先发出Pre-Vote请求询问整个系统。收到消息的节点根据日志判断它是否真的有机会赢得选举而不是在浪费时间。（判断逻辑和是否投票相同，即请求参数中的任期更大，或者任期相同但日志索引更大）。如果超过半数的节点同意Pre-Vote请求，则发起请求的节点才能真正发起新的选举。处于旧配置的集群由于日志不会更新，其他节点会拒绝它的Pre-Vote请求。

但只根据日志来判断并不能彻底解决该问题，如果领导者还没有把$C_{new}$复制到多数派节点，那么此时新集群的跟随者S2和S3依旧会同意旧集群的节点S1发起的Pre-Vote请求，S2将继续发起选举，干扰真正的领导者S4，使其转变为跟随者，影响集群正常工作，如下图所示。

![](https://github.com/yeyuhl/CloudComputingLabs/blob/master/Lab3/RaftKV/images/19.png?raw=true)

因此我们要增强Pre-Vote请求的判断条件，我们需要通过心跳来确定是否存在一个有效的领导者，在Raft算法中，如果一个领导者能够保持和其跟随者的心跳，则被认为是活跃的领导者，我们的系统不应该扰乱一个活跃的领导者。因此，如果一个服务器在选举超时时间内收到领导者的心跳，那么它将不会同意Pre-Vote请求，它可以不回复Pre-Vote请求或者直接回复拒绝。虽然这会导致每个服务器在开始选举之前，至少要多等待一个选举超时时间，但这有助于避免来自旧集群的服务器的干扰。

至此，一个节点同意Pre-Vote请求的条件是：

- 参数中的任期更大，或者任期相同但日志索引更大。

- 至少一次选举超时时间内没有收到领导者心跳。


Pre-Vote阶段的作用不止如此。网络分区也可能导致某个被孤立节点的任期与正常节点的任期差距拉大，因为该节点可能不断尝试选举而导致任期变得很大。网络恢复之后，由于该节点任期大于领导者任期，会导致领导者转为跟随者状态，但其实该节点日志并非最新的，不可能成为领导者，只会干扰系统的运行。通过新增Pre-Vote阶段，该节点在处于孤岛的情况下，无法获得多数派节点同意，自然无法发起选举，也不会—直增加自己的任期。待网络恢复后，就可以顺利地以跟随者身份加入集群。

## 日志压缩

Raft算法的日志在正常运行期间会不断增长，随着时间的推移，存储的日志会越来越多，不但占据很多磁盘空间，服务器重启或新服务器加入做日志重放也需要更多的时间。如果没有办法来压缩日志，则会导致可用性问题：要么磁盘空间被耗尽，要么花费太长时间才能启动一个新节点。所以日志压缩是必要的。

日志压缩的一般思路是，日志中的许多信息随着时间的推移会变成过时的，可以丢弃这些过时的日志。例如，一个将x设置为2的命令，如果在未来又有一个将x设置为3的命令，那么x＝2这个命令就被认为已经过时了，可以丢弃。状态机只关注最终的状态，一旦一个日志条目被提交并应用于状态机，那么用于到达最终状态的中间状态和命令就不再需要了，它们可以被压缩。

日志压缩后得到快照（Snapshot），快照也需要写入持久化存储。每个服务器会独立地创建自己的快照，快照中只包括已提交的日志。和配置变化不同，不同的系统有不同的日志压缩方式，这取决于系统的性能考量，以及日志存储是基于硬盘还是基于内存。日志压缩的大部分责任都落在状态机上。

无论使用何种压缩方法，都有几个核心的共同点：

- 第一，不将压缩任务集中在领导者上，每个服务器独立地压缩其已提交的日志。这就避免了领导者将日志传递给已有该日志的跟随者，同时增强了模块化，减少了交互，将整个系统的复杂性最小化。对于日志量非常小的状态机，基于领导者的日志压缩也许才会更好。

- 第二，Raft算法要保存最后一条被丢弃的日志条目的索引和任期，用于AppendEntries消
  息进行日志一致性检查。同时，也需要保存最新的配置信息，成员变更失败时需要回退配置，最近的配置必须保存。

- 第三，一旦丢弃了前面部分的日志，领导者就要承担两个新的责任：如果服务器重启了，则需要将最新的快照加载到状态机后再接收日志；需要向较慢的跟随者（日志远落后于领导者）发送一致的状态快照，使其跟上系统最新的状态。


# 实现基于Raft的kv存储

虽然我们有了创建简单RPC和简单DB的基础，但在该项目中我们并不会从0开始搭建，RPC部分采用阿里的sofa（dubbo需要Spring容器），DB部分采用RocksDB。后续代码讲解主要围绕RaftNode展开。

## 代码解读

### 选举相关

#### 1.func: updatePreElectionTime

首先，我们需要知道上一次选举超时时间preElectionTime。如果节点获悉选举超时，那么可以进行新的一轮选举。

其次，这里的关键在于，每次选举超时时间是随机的，即当前时间+随机值。之所以这么做的原因在于，原则上**节点可以无限重复分割选票**。假如选举同一时间开始，然后瓜分选票，没有达成任何多数派，又同一时间超时，同一时间再次选举，如此循环。类似于Paxos的活锁问题。那么，同样可以用解决活锁的办法来解决，即节点随机选择超时时间，选举超时时间通常在[ T , 2T ]区间之内（例如150～300ms）。由于随机性节点不太可能再同时开始竞选，所以先竞选的节点有足够的时间来索要其他节点的选票。如果T远远大于网络广播时间，那么效果更佳。

```java
private void updatePreElectionTime() {
    preElectionTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(20) * 100;
}
```

#### 2.func: requestVote

当收到其他节点的投票请求，我们需要对其进行处理。以下有几个关键点需要注意：

- 关注任期号，Raft算法只使用最新任期的信息。

- 关注是否已经投票了，一个任期内只能投票一次，不能重复投票。

- 关注日志信息，由于领导者会存在更替现象，当新的领导者上任后，日志可能就会变脏，因为前一任领导者可能在完成日志复制之前就宕机了。这就会出现不同节点中日志混乱的情况，而为了保证Raft算法的安全性，即如果某个日志条目在某个任期号己提交，那么这条记录必然出现在更大任期号的未来领导者的日志中。所以当上述情况，选出新的领导者后，我们希望领导者能**延迟提交**。而延迟提交的前提是要先修改选举程序，选出最佳领导者，即需要确保**领导者在超过半数投票给它的节点中拥有最完整的日志**，意思就是选出来的领导者的日志的索引和任期这对唯一标识是最大的，当然，这对标识中任期的优先级又更高一些，所以先判断任期是否更新。


```java
    public VoteResult requestVote(VoteParam param) {
        updatePreElectionTime();
        try {
            voteLock.lock();

            // 如果请求的任期号小于当前任期号(即请求者的任期号旧)，则拒绝投票
            if (param.getTerm() < term) {
                log.info("refuse to vote for candidate {} because of smaller term", param.getCandidateAddress());
                // 返回投票结果并更新对方的term
                return VoteResult.builder()
                        .term(term)
                        .voteGranted(false)
                        .build();
            }

            if ((StringUtil.isNullOrEmpty(votedFor) || votedFor.equals(param.getCandidateAddress()))) {
                // 索引和任期号唯一标识一条日志记录
                if (logModule.getLast() != null) {
                    // 对方最后的日志的任期号比自己小
                    if (logModule.getLast().getTerm() > param.getLastLogTerm()) {
                        log.info("refuse to vote for candidate {} because of older log term", param.getCandidateAddress());
                        return VoteResult.fail();
                    }
                    // 对方最后的日志的索引比自己小
                    if (logModule.getLastIndex() > param.getLastLogIndex()) {
                        log.info("refuse to vote for candidate {} because of older log index", param.getCandidateAddress());
                        return VoteResult.fail();
                    }
                }
                // 如果投票成功，切换状态
                status = FOLLOWER;
                stopHeartBeat();

                // 更新领导者，任期号和投票去向
                leader = param.getCandidateAddress();
                term = param.getTerm();
                votedFor = param.getCandidateAddress();
                log.info("vote for candidate: {}", param.getCandidateAddress());
                // 返回投票结果并更新对方的term
                return VoteResult.builder()
                        .term(term)
                        .voteGranted(true)
                        .build();
            }
            log.info("refuse to vote for candidate {} because there is no vote available", param.getCandidateAddress());
            return VoteResult.builder()
                    .term(term)
                    .voteGranted(false)
                    .build();
        } finally {
            updatePreElectionTime();
            voteLock.unlock();
        }
    }
```

#### 3.class: ElectionTask

设置一个定时执行的选举任务，每隔100ms执行一次。主要步骤如下：

- 判断是否要发起选举。如果自己就是领导者，那么不会发起选举（领导者发起选举会导致选举阶段出现权力真空，即领导者转换为候选人，当前没有领导者，如果这次选举不能选出领导者，那么这将是致命的）；如果现在就是在选举中，还没超过选举超时时间，也不会发起选举。

- 发起选举后，向其余节点发送投票请求（需要提供参选信息，即自己的任期号和日志信息），并先给自己投一票。

- 等待子线程返回投票结果，这里使用到了Semaphore，这里简单介绍一下Semaphore的作用。

  > Semaphore即信号量，它允许线程集等待，直到被允许继续运行为止。一个信号量管理多个许可(permit)。一般用于控制访问资源的线程总数。
  >
  > // 初始化许可为2，默认情况下是非公平锁
  >
  > Semaphore semaphore = new Semaphore(2);
  >
  > // 如果不传入获取数量，默认获取一个许可
  >
  > semaphore.acquire(); // 许可-1，目前为1
  >
  > // 如果不传入释放数量，默认释放一个许可
  >
  > semaphore.release(); // 许可+1，目前为2
  >
  > 如果目前许可为0，调用semaphore.acquire(int permits)的线程将会阻塞，直到可以获取permits个许可或者线程中断。但也有特殊方法，比如：
  >
  > // 第一个参数是要获取的许可数量，第二个是超时时间，第三个是时间单位
  >
  > semaphore.tryAcquire(int permits, long timeout, TimeUnit unit)
  >
  > 调用该方法后，如果没有足够的许可，则当前线程将出于线程调度目的而被禁用，并处于休眠状态，直到发生以下三种情况之一：
  >
  > 其它某个线程调用该信号量的release方法之一，当前线程接下来将被分配许可，并且可用许可的数量满足该请求；其它线程中断了该线程；等待时间超过timeout。
  >
  > 被唤醒后将根据是否获取到了足够的permits返回true或者false。

  在这里，我们使用的非公平的Semaphore，因为我们希望性能优先，并且使用tryAcquire来获取许可。

- 根据返回的投票结果进行分析：

    - 变成跟随者（任期号不够大或者日志不够完整）：如果自己的任期号不是最新的，则更新任期号，并将状态切换为跟随者。值得注意的是，如果在投票过程中，有其他节点发送了appendEntry，导致自己成为了跟随者，那么选举任务应该中止。

    - 变成领导者（获得了半数以上节点的投票）：启动心跳任务，向其余节点发送心跳信号，并完成leaderInit()，成为真正的领导者。


```java
class HeartBeatTask implements Runnable {
        @Override
        public void run() {

            if (status != LEADER) {
                return;
            }

            long current = System.currentTimeMillis();
            if (current - preHeartBeatTime < heartBeatInterval) {
                return;
            }

            preHeartBeatTime = System.currentTimeMillis();
            AppendParam param = AppendParam.builder()
                    .entries(null)// 心跳,空日志.
                    .leaderId(myAddress)
                    .term(term)
                    .leaderCommit(getCommitIndex())
                    .build();

            List<Future<Boolean>> futureList = new ArrayList<>();
            Semaphore semaphore = new Semaphore(0);

            for (String peer : peerAddress) {
                Request request = new Request(Request.APPEND_ENTRIES,
                        param,
                        peer);

                // 并行发起 RPC 复制并获取响应
                futureList.add(threadPoolExecutor.submit(() -> {
                    try {
                        AppendResult result = rpcClient.send(request);
                        long resultTerm = result.getTerm();
                        if (resultTerm > term) {
                            log.warn("follow new leader {}", peer);
                            term = resultTerm;
                            votedFor = "";
                            status = FOLLOWER;
                        }
                        semaphore.release();
                        return result.isSuccess();
                    } catch (Exception e) {
                        log.error("heartBeatTask RPC Fail, request URL : {} ", request.getUrl());
                        semaphore.release();
                        return false;
                    }
                }));
            }

            try {
                // 等待任务线程执行完毕，这里设置一半是为了避免方法提前返回或者等待时间
                semaphore.tryAcquire((int) Math.floor((peerAddress.size() + 1) / 2), 2000, MILLISECONDS);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

            //  心跳响应成功，通知阻塞的线程
            if (waitThreads.size() > 0) {
                int success = getReplicationResult(futureList);
                if (success * 2 >= peerAddress.size()) {
                    synchronized (consistencySignal) {
                        consistencySignal.notifyAll();
                    }
                } else {
                    Thread waitThread;
                    while ((waitThread = getWaitThread()) != null) {
                        waitThread.interrupt();
                    }
                }
            }
        }
    }
```

### 领导者事务

#### 1.func: leaderInit

当新的领导者上台，需要处理日志提交和清理不一致日志的问题。

- 回到前面requestVote所提及的问题，为了解决领导者更替所带来的日志重复提交问题，我们想出了**延迟提交**来解决，即新领导者需要满足以下约束条件才能提交日志：日志必须存储在超过半数的节点上；领导者必须看到超过半数的节点上还存储着至少一条自己任期内的日志。虽然领导者只能提交自己任期的日志，但实际上也间接提交了之前任期的日志，避免了重复提交日志。但问题也来了，领导者只能提交自己任期的日志，如果这个任期内客服端没有发送请求，那么就没有日志产生，之前的日志也无法提交，系统就阻塞在这里了。因此，领导者刚选举成功的时候，不管是否有客户端请求，立即向自己本地追加一条**no-op空日志**，并立即将no-op空日志复制到其他节点。no-op空日志属于领导者任期的日志，多数派达成后立即提交，no-op空日志前面的那些之前任期未提交的日志全部间接提交了，问题也就解决了。

- 系统中通常有两种不一致的日志：**缺失的条目**（Missing Entries）和**多出来的日志条目**（Extraneous Entries）。新的领导者必须使跟随者的日志与自己的日志保持一致，我们要清理的就是这两种日志。对于缺失的条目，Raft算法会发送AppendEntries消息来补齐；对于多出来的条目，Raft算法会想办法删除。为了清理不一致的日志，领导者会为每个跟随者保存变量**nextIndex[]**，用来表示要发送给该跟随者的下一个日志条目的索引。对于跟随者i来说，领导者上的nextIndex[i]的初始值为**1＋领导者最后一条日志的索引**。

    - Missing Entries：递减nextIndex[i]并带上前一个日志条目的唯一标识和跟随者i对应索引处的日志条目进行比对，不匹配则继续递减nextIndex[i]，直到在某个索引处匹配成功。那么就从nextIndex[i]对应的地方开始补充缺失的日志条目。

    - Extraneous Entries：原理相同，不过是先删除nextIndex[i]对应的索引及后面所有的日志条目，然后再复制上去。


不过在leaderInit该方法中，真正解决的只是第一个问题，发送并提交no-op空日志，以提交旧领导者未提交的日志。第二个问题只是初始化了所有的nextIndex，真正解决要在领导者的replication方法和跟随者的appendEntries方法中。

```java
    private boolean leaderInit() {
        leaderInitializing = true;
        nextIndexes = new ConcurrentHashMap<>();
        for (String peer : peerAddress) {
            nextIndexes.put(peer, logModule.getLastIndex() + 1);
        }

        // no-op 空日志
        LogEntry logEntry = LogEntry.builder()
                .command(null)
                .term(term)
                .build();

        // 写入本地日志并更新logEntry的index
        logModule.write(logEntry);
        log.info("write no-op log success, log index: {}", logEntry.getIndex());

        List<Future<Boolean>> futureList = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0);

        //  复制到其他机器
        for (String peer : peerAddress) {
            // 并行发起 RPC 复制并获取响应
            futureList.add(replication(peer, logEntry, semaphore));
        }

        try {
            // 等待replicationResult中的线程执行完毕
            semaphore.tryAcquire((int) Math.floor((peerAddress.size() + 1) / 2), 6000, MILLISECONDS);
        } catch (InterruptedException e) {
            log.error("semaphore timeout in leaderInit()");
        }

        // 统计日志复制结果
        int success = getReplicationResult(futureList);

        //  响应客户端(成功一半及以上)
        if (success * 2 >= peerAddress.size()) {
            // 提交旧日志并更新 commit index
            long nextCommit = getCommitIndex() + 1;
            while (nextCommit < logEntry.getIndex() && logModule.read(nextCommit) != null) {
                stateMachine.apply(logModule.read(nextCommit));
                nextCommit++;
            }
            setCommitIndex(logEntry.getIndex());
            log.info("no-op successfully commit, log index: {}", logEntry.getIndex());
            leaderInitializing = false;
            return true;
        } else {
            // 提交失败，删除日志，重新发起选举
            logModule.removeOnStartIndex(logEntry.getIndex());
            log.warn("no-op commit fail, election again");
            status = FOLLOWER;
            votedFor = "";
            updatePreElectionTime();
            stopHeartBeat();
            leaderInitializing = false;
            return false;
        }

    }
```

#### 2.func: replication

既然是领导者，那么领导者还要考虑怎么把日志复制到其他节点上面。主要有以下步骤：

- 对各种请求进行封装并获取相关日志为复制做准备。封装AppendParam和RPC请求，然后获取要复制的日志数组，并初始化一个preLog，其term和index均为-1。

- 发送RPC请求并获取AppendResult，根据返回的结果进行处理。

    - 返回结果为null，可能调用超时，也可能发生其他预料外的情况。

    - 返回结果为true，更新nextIndexes，并release信号量。

    - 返回结果为false，具体分析失败情况，5s后重试。

      ①日志所要复制上去的节点的任期号比本节点大，说明本节点可能中途断连了，主动切换到跟随者状态。

      ②nextindex[i]前一个日志与所要复制上去的节点的当前索引的日志不匹配，需要让nextindex[i]递减以找到可以开始复制的位置。这里无论是缺失还是多余都统一处理，对缺失而言就是添加，对多余而言就是覆盖。


```java
    public Future<Boolean> replication(String peer, LogEntry entry, Semaphore semaphore) {
        return threadPoolExecutor.submit(() -> {
            long start = System.currentTimeMillis();
            long end = start;

            // 1.封装appendEntries请求基本参数
            AppendParam appendParam = AppendParam.builder()
                    .leaderId(myAddress)
                    .term(term)
                    .leaderCommit(getCommitIndex())
                    .serverId(peer)
                    .build();

            // 2.生成日志数组
            Long nextIndex = nextIndexes.get(peer);
            List<LogEntry> logEntryList = new ArrayList<>();
            if (entry.getIndex() >= nextIndex) {
                for (long i = nextIndex; i <= entry.getIndex(); i++) {
                    LogEntry log = logModule.read(i);
                    if (log != null) {
                        logEntryList.add(log);
                    }
                }
            } else {
                logEntryList.add(entry);
            }

            // 3.设置preLog相关参数，用于日志匹配
            LogEntry preLog = getPreLog(logEntryList.get(0));
            // preLog不存在时，下述参数会被设为-1
            appendParam.setPreLogTerm(preLog.getTerm());
            appendParam.setPrevLogIndex(preLog.getIndex());

            // 4.封装RPC请求
            Request request = Request.builder()
                    .cmd(Request.APPEND_ENTRIES)
                    .obj(appendParam)
                    .url(peer)
                    .build();

            // preLog不匹配时重试，重试超时时间为5s
            while (end - start < 5 * 1000L) {
                appendParam.setEntries(logEntryList.toArray(new LogEntry[0]));
                try {
                    // 5. 发送RPC请求，同步调用，阻塞直到得到返回值或超时
                    AppendResult result = rpcClient.send(request);
                    if (result == null) {
                        log.error("follower responses with null result, request URL : {} ", peer);
                        semaphore.release();
                        return false;
                    }
                    if (result.isSuccess()) {
                        log.info("append follower entry success, follower=[{}], entry=[{}]", peer, appendParam.getEntries());
                        // 更新索引信息
                        nextIndexes.put(peer, entry.getIndex() + 1);
                        semaphore.release();
                        return true;
                    } else {
                        // 失败情况1：对方任期号比我大，转变成跟随者
                        if (result.getTerm() > term) {
                            log.warn("follower [{}] term [{}], my term = [{}], so I will become follower",
                                    peer, result.getTerm(), term);
                            term = result.getTerm();
                            status = FOLLOWER;
                            stopHeartBeat();
                            semaphore.release();
                            return false;
                        } else {
                            // 失败情况2：preLog不匹配
                            nextIndexes.put(peer, Math.max(nextIndex - 1, 0));
                            log.warn("follower {} nextIndex not match, will reduce nextIndex and retry append, nextIndex : [{}]", peer,
                                    logEntryList.get(0).getIndex());

                            // 更新preLog和logEntryList
                            LogEntry l = logModule.read(logEntryList.get(0).getIndex() - 1);
                            if (l != null) {
                                // 直接往索引0处插入新LogEntry即可，后面的元素会自动后移
                                logEntryList.add(0, l);
                            } else {
                                // l == null 说明前一次发送的preLogIndex已经来到-1的位置，正常情况下应该无条件匹配
                                log.error("log replication from the beginning fail");
                                semaphore.release();
                                return false;
                            }

                            preLog = getPreLog(logEntryList.get(0));
                            appendParam.setPreLogTerm(preLog.getTerm());
                            appendParam.setPrevLogIndex(preLog.getIndex());
                        }
                    }

                    end = System.currentTimeMillis();

                } catch (Exception e) {
                    log.error("Append entry RPC fail, request URL : {} ", peer);
                    semaphore.release();
                    return false;
                }
            }

            // 超时了
            log.error("replication timeout, peer {}", peer);
            semaphore.release();
            return false;
        });
    }
```

#### 3.func: redirect

当客户端请求到来时，写操作或者某些读操作跟随者无法处理（缺失日志，需要先同步），就需要调用redirect方法，将该请求转交给领导者处理。

```java
    public ClientResponse redirect(ClientRequest request) {
        if (status == FOLLOWER && !StringUtil.isNullOrEmpty(leader)) {
            return ClientResponse.redirect(leader);
        } else {
            return ClientResponse.fail();
        }
    }
```

#### 4.func: handleSyncRequest

前面提到过，如果某些请求，跟随者无法处理有可能是因为没有同步日志，导致跟随者无法向客户端提供它所想要读取的数据，因此跟随者会向领导者发起日志同步请求。

- 等待一个心跳周期，确保当前领导者有效。我们假设这么一个情况，系统发生网络分区时，可能存在两个领导者。旧领导者仍然在工作，但集群的其他部分已经选举出一个新的领导者，并提交了新的日志条目。如果此时客户端联系的领导者恰好是旧领导者，那么旧领导仍然会认为自己是权威的，它将直接返回旧的结果给客户端，这显然不满足线性一致性。线性一致性要求读操作必须返回最近一次提交的写操作的结果。在Raft算法中，其写操作一定得从领导者向跟随者同步，因此写操作相关的请求需要交给领导者处理。但对于读操作，Raft的默认方式是从领导者读，这样就能够满足线性一致性，但是这样的实现方式也会导致读吞吐也不能随节点个数的增长而线性提升，说人话来说就是性能低下。当然Raft也给出了解决方案，一般是两种方式：

    - Read Index：

      领导者：记录当前的commitIndex，称为readIndex。向follower发起一次心跳，如果大多数节点回复了，那就能确定现在仍然是leader。等待状态机至少应用到readIndex记录的Log。执行读请求，将结果返回给Client。

      跟随者：向leader请求其commitIndex来作为本次查询请求的readIndex。该 leader向 follower发起一次心跳，如果大多数节点回复了，那就能确定现在仍然是 leader。该leader返回本地的commitIndex给follower。等待状态机至少应用到 readIndex记录的Log。执行读请求，将结果返回给Client。

      上述流程中领导者主动发起心跳，主要作用是为了让领导者确保自己仍是领导者。避免出现前文提到的现象，从而导致不满足线性一致性。

    - Lease Read：

      Read Index的缺点在于，增加了网络消息轮次。而Lease Read则更进一步，省去了网络交互。基本的思路是 leader 取一个比 Election Timeout 小的租期，在租期内不会发生选举，确保 leader 在这个周期不会变，所以就算老 leader 被分区到了少数派，直接读状态机也一定不会读到旧数据，因为租期内新 leader 一定还没有产生，也就不会有更新的数据了。因此 Lease Read 可以跳过 Read Index 流程的第二步，从而降低读延时提升读吞吐量。不过 Lease Read 的正确性和时间挂钩，因此时间的准确性至关重要，如果时钟漂移严重，这套机制就会有问题。Raft论文中提到，除非是为了满足性能要求，否则不建议使用这种替代方式。

- 复制日志到跟随者，无需多言。


```java
    public SyncResponse handleSyncRequest(SyncParam param) {
        if (status != LEADER) {
            return SyncResponse.fail();
        }
        synchronized (consistencySignal) {
            try {
                // 等待一个心跳周期，确保当前领导者有效
                log.warn("leader check");
                waitThreads.push(Thread.currentThread());
                consistencySignal.wait();
            } catch (InterruptedException e) {
                log.error("thread has been interrupted: {}", e.getMessage());
                return SyncResponse.fail();
            }
        }
        if (param.getFollowerIndex() == getCommitIndex()) {
            return SyncResponse.success();
        }
        // 复制日志到follower
        List<Future<Boolean>> futureList = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0);
        futureList.add(replication(param.getFollowerId(),
                LogModule.getInstance().read(getCommitIndex()),
                semaphore));
        try {
            // 等待replication中的线程执行完毕
            semaphore.tryAcquire(1, 6000, MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            return SyncResponse.fail();
        }
        if (getReplicationResult(futureList) == 1) {
            return SyncResponse.success();
        } else {
            return SyncResponse.fail();
        }
    }
```

#### 5.class: HeartBeatTask

领导者定时触发心跳任务，通过心跳信号（RPC 调用）维护集群的日志提交状态。

```java
    class HeartBeatTask implements Runnable {
        @Override
        public void run() {

            if (status != LEADER) {
                return;
            }

            long current = System.currentTimeMillis();
            if (current - preHeartBeatTime < heartBeatInterval) {
                return;
            }

            preHeartBeatTime = System.currentTimeMillis();
            AppendParam param = AppendParam.builder()
                    .entries(null)// 心跳,空日志.
                    .leaderId(myAddress)
                    .term(term)
                    .leaderCommit(getCommitIndex())
                    .build();

            List<Future<Boolean>> futureList = new ArrayList<>();
            Semaphore semaphore = new Semaphore(0);

            for (String peer : peerAddress) {
                Request request = new Request(Request.APPEND_ENTRIES,
                        param,
                        peer);

                // 并行发起 RPC 复制并获取响应
                futureList.add(threadPoolExecutor.submit(() -> {
                    try {
                        AppendResult result = rpcClient.send(request);
                        long resultTerm = result.getTerm();
                        if (resultTerm > term) {
                            log.warn("follow new leader {}", peer);
                            term = resultTerm;
                            votedFor = "";
                            status = FOLLOWER;
                        }
                        // 释放信号量，为后面tryAcquire()铺垫
                        semaphore.release();
                        return result.isSuccess();
                    } catch (Exception e) {
                        log.error("heartBeatTask RPC Fail, request URL : {} ", request.getUrl());
                        semaphore.release();
                        return false;
                    }
                }));
            }

            try {
                // 等待任务线程执行完毕
                semaphore.tryAcquire((int) Math.floor((peerAddress.size() + 1) / 2), 2000, MILLISECONDS);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

            //  心跳响应成功，通知阻塞的线程
            if (waitThreads.size() > 0) {
                int success = getReplicationResult(futureList);
                if (success * 2 >= peerAddress.size()) {
                    synchronized (consistencySignal) {
                        consistencySignal.notifyAll();
                    }
                } else {
                    Thread waitThread;
                    while ((waitThread = getWaitThread()) != null) {
                        waitThread.interrupt();
                    }
                }
            }
        }
    }
```

### 跟随者事务

#### 1.func: appendEntries

跟随者需要处理来自其他节点的非选举请求，比如说心跳信号，追加日志请求。

- 心跳信号。如果传入的AppendParam中的LogEntry为空，说明这是一个心跳请求，领导者通过心跳信号来触发跟随者的日志同步。如果本节点的下一次提交索引小于等于领导者的提交索引，又或者是对应的日志为空。那么说明跟随者还没将日志同步到最新，此时同步日志并通知领导者同步完成。

- 追加日志。如果传入的AppendParam中的LogEntry不为空，说明这是一个日志追加请求。这里实际操作过程就是前面提到的处理**日志不一致**问题。

    - 检查preLog。领导者传来的preLog的index和本节点的lastIndex做比较，如果lastIndex小于等于preLog的index，那么继续查看任期号是不是匹配，如果不匹配则通知领导者把nextIndex-1，继续找一个匹配的。注意一个特殊情况，如果preLog的index为-1，说明从第一个日志开始复制，此时必然是能够匹配上的。

    - 清理旧日志并追加日志。只保留[0,prevLogIndex]范围内的日志，然后在后面添加新的日志。

    - 提交日志。我们前面只是把日志存储了起来，我们这时候还要将日志里所涉及的对数据的写操作给实现，即对数据库里存储的数据进行更新。

    - 最后返回一个追加成功的响应给领导者。


值得注意的是，调用appendEntries方法会打断选举（因为正在进行日志同步等操作，优先保障），因此我们需要更新preElectionTime。

```java
    public AppendResult appendEntries(AppendParam param) {
        updatePreElectionTime();
        preHeartBeatTime = System.currentTimeMillis();
        AppendResult result = AppendResult.fail();
        try {
            appendLock.lock();
            result.setTerm(term);

            if (param.getTerm() < term) {
                log.info("refuse to append entries from leader {} because of smaller term", param.getLeaderId());
                return result;
            }

            leader = param.getLeaderId();
            votedFor = "";

            if (status != FOLLOWER) {
                log.info("node {} become FOLLOWER, term : {}, param Term : {}", myAddress, term, param.getTerm());
                status = FOLLOWER;
                stopHeartBeat();
            }

            term = param.getTerm();

            // 如果entries为空，表示心跳，领导者通过心跳来触发跟随者的日志同步
            if (param.getEntries() == null || param.getEntries().length == 0) {
                long nextCommit = getCommitIndex() + 1;
                while (nextCommit <= param.getLeaderCommit() && logModule.read(nextCommit) != null) {
                    stateMachine.apply(logModule.read(nextCommit));
                    nextCommit++;
                }
                setCommitIndex(nextCommit - 1);
                return AppendResult.builder()
                        .term(term)
                        .success(true)
                        .build();
            }

            // 如果entries不为空，表示追加日志
            // 1.首先判断preLog
            if (logModule.getLastIndex() < param.getPrevLogIndex()) {
                log.info("refuse to append entries from leader {} because of smaller last index", param.getLeaderId());
                return result;
            } else if (param.getPrevLogIndex() >= 0) {
                // 如果prevLogIndex在跟随者的日志索引范围内，判断该日志的任期号是否相同
                LogEntry preEntry = logModule.read(param.getPrevLogIndex());
                // 任期号并不匹配，领导者将选取更早的preLog并重试
                if (preEntry.getTerm() != param.getPreLogTerm()) {
                    log.info("refuse to append entries from leader {} because of different term", param.getLeaderId());
                    return result;
                }
            }

            // 2.清理多余的旧日志
            long currIndex = param.getPrevLogIndex() + 1;
            if (logModule.read(currIndex) != null) {
                // 只保留[0, prevLogIndex]之内的日志
                logModule.removeOnStartIndex(currIndex);
            }

            // 3.追加日志
            LogEntry[] entries = param.getEntries();
            for (LogEntry logEntry : entries) {
                logModule.write(logEntry);
            }

            // 4.提交旧日志
            long nextCommit = getCommitIndex() + 1;
            while (nextCommit <= param.getLeaderCommit() && logModule.read(nextCommit) != null) {
                stateMachine.apply(logModule.read(nextCommit));
                nextCommit++;
            }
            setCommitIndex(nextCommit - 1);

            // 5.追加成功，响应成功
            result.setSuccess(true);
            return result;
        } finally {
            updatePreElectionTime();
            appendLock.unlock();
        }
    }
```

### 客户端请求

#### 1.func: propose

对客户端的请求进行处理，节点处于不同状态会有不同的处理结果。

- 跟随者：仅处理GET指令（读操作），PUT指令或DEL指令（写操作）转交给领导者处理。在进行读操作前需要和领导者进行日志同步，而领导者需要完成一次心跳信号确认自己是领导者后才会响应跟随者。跟随者从数据库中读取数据并返回给客户端。如果这个过程中读取失败，跟随者会直接将这个读操作转交给领导者处理。

- 候选人：不处理任何事务，直接响应失败。

- 领导者：领导者可以处理读操作，也可以处理写操作。读操作和跟随者实现差别不大，都要等待一次心跳信号。写操作会先进行**检验操作**，确保该操作仅apply一次（注意事项部分会详细说明），然后将日志写入本地并将日志复制到其他节点。紧接着等待日志复制结果返回，如果半数以上的节点都复制了该日志，那么领导者就会将其应用到状态机，即对数据进行修改。最后响应客户端。


```java
    public synchronized ClientResponse propose(ClientRequest request) {
        log.info("handlerClientRequest handler {} operation, key: [{}], value: [{}]",
                ClientRequest.Type.value(request.getType()), request.getKey(), request.getValue());

        // follower状态下处理客户端请求
        if (status == FOLLOWER) {
            if (request.getType() == ClientRequest.PUT) {
                log.warn("follower can not write, redirect to leader: {}", leader);
                return redirect(request);
            } else if (request.getType() == ClientRequest.GET) {
                SyncParam syncParam = SyncParam.builder()
                        .followerId(myAddress)
                        .followerIndex(getCommitIndex())
                        .build();
                Request syncRequest = Request.builder()
                        .cmd(Request.FOLLOWER_SYNC)
                        .obj(syncParam)
                        .url(leader)
                        .build();
                try {
                    SyncResponse syncResponse = rpcClient.send(syncRequest, 7000);
                    if (syncResponse.isSuccess()) {
                        log.warn("follower read success");
                        String value = stateMachine.get(request.getKey());
                        if (value != null) {
                            return ClientResponse.success(value);
                        }
                        return ClientResponse.success(null);
                    }
                } catch (RemotingException | InterruptedException e) {
                    log.warn("sync with leader fail, follower: {}", myAddress);
                }
                log.warn("follower read fail, redirect to leader: {}", leader);
                return redirect(request);
            }
        }

        // candidate状态下处理客户端请求
        else if (status == CANDIDATE) {
            log.warn("candidate declines client request: {} ", request);
            return ClientResponse.fail();
        }

        // leader状态下处理客户端请求
        if (leaderInitializing) {
            // leader正在初始化，拒绝请求
            log.warn("leader is initializing, please try again later");
            return ClientResponse.fail();
        }
        // 读操作
        if (request.getType() == ClientRequest.GET) {
            synchronized (consistencySignal) {
                try {
                    // 等待一个心跳周期，以保证当前领导者有效
                    log.warn("leader check");
                    waitThreads.push(Thread.currentThread());
                    consistencySignal.wait();
                } catch (InterruptedException e) {
                    log.error("thread has been interrupted: {}", e.getMessage());
                    return ClientResponse.fail();
                }
                String value = stateMachine.get(request.getKey());
                if (value != null) {
                    return ClientResponse.success(value);
                }
                return ClientResponse.success(null);
            }
        }
        // 写操作之前，先进行检验操作，避免进行重复执行写操作
        if (stateMachine.get(request.getRequestId()) != null) {
            log.info("request have been ack");
            return ClientResponse.success();
        }
        // 写操作
        LogEntry logEntry = LogEntry.builder()
                .command(Command.builder()
                        .key(request.getKey())
                        .value(request.getValue())
                        .type(CommandType.PUT)
                        .build())
                .term(term)
                .requestId(request.getRequestId())
                .build();
        // 将日志写入本地日志文件，并更新index
        logModule.write(logEntry);
        log.info("write logModule success, logEntry info : {}, log index : {}", logEntry, logEntry.getIndex());

        List<Future<Boolean>> futureList = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0);

        // 将日志复制到其他节点
        for (String peer : peerAddress) {
            // 并行发起RPC复制并获取响应
            futureList.add(replication(peer, logEntry, semaphore));
        }

        try {
            semaphore.tryAcquire((int) Math.floor((peerAddress.size() + 1) / 2), 6000, MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }

        // 统计日志复制结果
        int success = getReplicationResult(futureList);

        if (success * 2 >= peerAddress.size()) {
            // 更新
            setCommitIndex(logEntry.getIndex());
            // 应用到状态机
            stateMachine.apply(logEntry);
            log.info("successfully commit, logEntry info: {}", logEntry);
            // 返回成功
            return ClientResponse.success();
        } else {
            logModule.removeOnStartIndex(logEntry.getIndex());
            log.warn("commit fail, logEntry info:{}", logEntry);
            // 响应客户端
            return ClientResponse.fail();
        }
    }
```

## 注意事项

- **并发编程**：涉及了很多并发相关的知识，比如使用了线程池，信号量和ReentrantLock。浅述一下相关知识点，在requestVote和appendEntries这两个方法中，分别使用了ReentrantLock进行上锁。我们要避免多个线程同时执行，对共享资源进行修改。ReentrantLock是一把可重入的独占锁，同时只有一个线程能获取，其余想获取的线程将被阻塞而被放入该锁的AQS阻塞队列里面，默认情况下是非公平锁。而对于propose和leaderInit我们也使用synchronized关键字。对于领导者等待一次心跳信号，我们使用了synchronized(obj){obj.wait()}使当前线程进入等待，直到线程池定时执行心跳任务，调用了synchronized(obj){obj.notify()}将其唤醒。信号量前面已经详细阐述了，此处不再赘述。还有一个经常使用的便是Future接口，它可以返回我们线程池执行任务的结果，我们可以通过其isDone()方法以轮询的形式查看任务是否完成，更详细地可以看前言中RPC的项目。

- **去重操作**：我们在状态机的apply方法的最后，加了这么一段代码``put(logEntry.getRequestId(), "1");``其作用就是记录请求id，保证该请求中的操作仅执行一次。有人可能认为，只要写请求是幂等的，那重复执行多次也是可以满足线性一致性的，实际上则不然。考虑这样一个例子：对于一个仅支持 put 和 get 接口的 raftKV 系统，其每个请求都具有幂等性。设 x 的初始值为 0，此时有两个并发客户端，客户端 1 执行 put(x,1)，客户端 2 执行 get(x) 再执行 put(x,2)，问（客户端 2 读到的值，x 的最终值）是多少。对于线性一致的系统，答案可以是 (0,1)，(0,2) 或 (1,2)。然而，如果客户端 1 执行 put 请求时发生了上段描述的情况，然后客户端 2 读到 x 的值为 1 并将 x 置为了 2，最后客户端 1 超时重试且再次将 x 置为 1。对于这种场景，答案是 (1,1)，这就违背了线性一致性。归根究底还是由于幂等的 put(x,1) 请求在状态机上执行了两次，有两个 LZ 点。因此，即使写请求的业务语义能够保证幂等，不进行额外的处理让其重复执行多次也会破坏线性一致性。当然，读请求由于不改变系统的状态，重复执行多次是没问题的。我们需要保证日志仅被执行一次，即它可以被 commit 多次，但一定只能 apply 一次。实现方法也很简单，对clientRequest进行唯一标识，并持久化。在propose方法中，进行写操作前，就需要判断``stateMachine.get(request.getRequestId()) != null``，如果不为null，说明该请求已经执行过了，不再执行。clientRequest在这里通过UUID和一个计数器变量简单构成随机性，在实际生产中应该使用snowflake这种算法更为贴切。

- **配置变更/日志压缩**：由于不是实际生产环境，因此配置变更和日志压缩其实都没有实现。

- **启动方式**：在 VM 参数中配置节点端口号，如 `-Dserver.port=8775`，在 8775、8776、8777、8778、8779端口下运行 `RaftNodeBootStrap`，客户端为`RaftClient`。
