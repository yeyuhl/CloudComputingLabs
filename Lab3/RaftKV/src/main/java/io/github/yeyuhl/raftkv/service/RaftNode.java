package io.github.yeyuhl.raftkv.service;

import com.alipay.remoting.exception.RemotingException;
import io.github.yeyuhl.raftkv.service.config.RaftConfig;
import io.github.yeyuhl.raftkv.service.constant.Command;
import io.github.yeyuhl.raftkv.service.constant.CommandType;
import io.github.yeyuhl.raftkv.service.constant.NodeStatus;
import io.github.yeyuhl.raftkv.service.database.StateMachine;
import io.github.yeyuhl.raftkv.service.dto.append.AppendParam;
import io.github.yeyuhl.raftkv.service.dto.append.AppendResult;
import io.github.yeyuhl.raftkv.service.dto.client.ClientRequest;
import io.github.yeyuhl.raftkv.service.dto.client.ClientResponse;
import io.github.yeyuhl.raftkv.service.dto.rpc.Request;
import io.github.yeyuhl.raftkv.service.dto.sync.SyncParam;
import io.github.yeyuhl.raftkv.service.dto.sync.SyncResponse;
import io.github.yeyuhl.raftkv.service.dto.vote.VoteParam;
import io.github.yeyuhl.raftkv.service.dto.vote.VoteResult;
import io.github.yeyuhl.raftkv.service.log.LogEntry;
import io.github.yeyuhl.raftkv.service.log.LogModule;
import io.github.yeyuhl.raftkv.service.rpc.RpcClient;
import io.github.yeyuhl.raftkv.service.rpc.RpcServer;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.yeyuhl.raftkv.service.constant.NodeStatus.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * RaftNode是Raft集群中的节点
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Slf4j
public class RaftNode {
    /**
     * 心跳间隔时间
     */
    private int heartBeatInterval;

    /**
     * 选举超时时间
     */
    private int electionTimeout;

    /**
     * 节点状态
     */
    private volatile NodeStatus status;

    /**
     * 当前任期号
     */
    private volatile long term;

    /**
     * 获得选票的候选人Id
     */
    private volatile String votedFor;

    /**
     * 领导者地址
     */
    private volatile String leader;

    /**
     * 上次选举超时时间
     */
    private long preElectionTime;

    /**
     * 上次心跳间隔时间
     */
    private long preHeartBeatTime;

    /**
     * 集群其它节点地址，格式："ip:port"
     */
    private List<String> peerAddress;

    /**
     * 对于每一个服务器，需要发送给他的下一个日志条目的索引值
     */
    Map<String, Long> nextIndexes;

    /**
     * 当前节点地址
     */
    private String myAddress;

    /**
     * 日志模块
     */
    LogModule logModule;

    /**
     * 状态机
     */
    StateMachine stateMachine;

    /**
     * 线程池
     */
    private ScheduledExecutorService scheduledExecutorService;
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 定时任务
     */
    HeartBeatTask heartBeatTask;
    ElectionTask electionTask;
    ScheduledFuture<?> heartBeatFuture;

    /**
     * RPC 客户端
     */
    private RpcClient rpcClient;

    /**
     * RPC 服务端
     */
    private RpcServer rpcServer;

    /**
     * 一致性信号
     */
    private final Integer consistencySignal = 1;

    /**
     * 等待被一致性信号唤醒的线程
     */
    private final Stack<Thread> waitThreads = new Stack<>();

    /**
     * 处理选举请求的锁
     */
    private final ReentrantLock voteLock = new ReentrantLock();

    /**
     * 处理日志请求的锁
     */
    private final ReentrantLock appendLock = new ReentrantLock();

    /**
     * 领导者初始化信号
     */
    private boolean leaderInitializing;

    public RaftNode() {
        logModule = LogModule.getInstance();
        stateMachine = StateMachine.getInstance();
        setConfig();
        threadPoolInit();
        log.info("Raft node started successfully. The current term is {}", term);
    }

    /**
     * 设置配置
     */
    private void setConfig() {
        heartBeatInterval = RaftConfig.HEARTBEAT_INTERVAL;
        electionTimeout = RaftConfig.ELECTION_TIMEOUT;
        updatePreElectionTime();
        preHeartBeatTime = System.currentTimeMillis();
        status = FOLLOWER;
        String port = System.getProperty("server.port");
        myAddress = "localhost:" + port;
        rpcServer = new RpcServer(Integer.parseInt(port), this);
        rpcClient = new RpcClient();
        peerAddress = RaftConfig.getAddressList();
        peerAddress.remove(myAddress);
        LogEntry last = logModule.getLast();
        if (last != null) {
            term = last.getTerm();
        }
    }

    /**
     * 更新上次选举超时时间
     */
    private void updatePreElectionTime() {
        // 之所以要加上随机时间，是为了避免多个节点同时发起选举
        // 当前时间+随机时间（0到20乘以100）
        preElectionTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(20) * 100;
    }

    /**
     * 初始化线程池
     */
    private void threadPoolInit() {
        // 线程池参数
        int cpu = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = cpu * 2;
        final int queueSize = 1024;
        final long keepTime = 1000 * 60;

        // 创建线程池
        scheduledExecutorService = new ScheduledThreadPoolExecutor(cpu);
        threadPoolExecutor = new ThreadPoolExecutor(
                cpu,
                maxPoolSize,
                keepTime,
                MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(queueSize));
        // 创建心跳任务
        heartBeatTask = new HeartBeatTask();
        // 创建选举任务
        electionTask = new ElectionTask();
        // 每隔100ms执行一次选举任务，首次执行延迟3s
        scheduledExecutorService.scheduleAtFixedRate(electionTask, 3000, 100, MILLISECONDS);
    }

    /**
     * 处理来自其它节点的投票请求，决定是否投票
     */
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

    /**
     * 处理来自其它节点的非选举请求(心跳或者追加日志)
     */
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

    /**
     * 处理来自客户端的请求
     */
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

    /**
     * 处理follower日志同步请求
     */
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

    /**
     * 转发给leader处理（重定向）
     */
    public ClientResponse redirect(ClientRequest request) {
        if (status == FOLLOWER && !StringUtil.isNullOrEmpty(leader)) {
            return ClientResponse.redirect(leader);
        } else {
            return ClientResponse.fail();
        }
    }

    /**
     * 1. 初始化所有的 nextIndex 值为自己的最后一条日志的 index + 1
     * 2. 发送并提交no-op空日志，以提交旧领导者未提交的日志
     * 3. apply no-op之前的日志
     */
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

    /**
     * 发起选举
     */
    class ElectionTask implements Runnable {
        @Override
        public void run() {

            // leader状态下不允许发起选举
            if (status == LEADER) {
                return;
            }

            // 判断是否超过选举超时时间
            long current = System.currentTimeMillis();
            if (current - preElectionTime < electionTimeout) {
                return;
            }

            status = CANDIDATE;
            leader = "";
            term++;
            votedFor = myAddress;
            log.info("node become CANDIDATE and start election, its term : [{}], LastEntry : [{}]",
                    term, logModule.getLast());
            ArrayList<Future<VoteResult>> futureList = new ArrayList<>();

            // 信号量，用于等待子线程完成选票统计
            Semaphore semaphore = new Semaphore(0);

            // 发送投票请求
            for (String peer : peerAddress) {
                // 执行rpc调用并加入list；添加的是submit的返回值
                futureList.add(threadPoolExecutor.submit(() -> {
                    long lastLogTerm = 0L;
                    long lastLogIndex = 0L;
                    LogEntry lastLog = logModule.getLast();
                    if (lastLog != null) {
                        lastLogTerm = lastLog.getTerm();
                        lastLogIndex = lastLog.getIndex();
                    }

                    // 封装请求体
                    VoteParam voteParam = VoteParam.builder()
                            .term(term)
                            .candidateAddress(myAddress)
                            .lastLogIndex(lastLogIndex)
                            .lastLogTerm(lastLogTerm)
                            .build();

                    Request request = Request.builder()
                            .cmd(Request.RAFT_VOTE)
                            .obj(voteParam)
                            .url(peer)
                            .build();

                    try {
                        // rpc 调用
                        return rpcClient.send(request);
                    } catch (Exception e) {
                        log.error("ElectionTask RPC Fail , URL : " + request.getUrl());
                        return null;
                    } finally {
                        // 释放信号量，为后面tryAcquire()铺垫，即统计有多少个线程执行完毕
                        semaphore.release();
                    }
                }));
            }

            try {
                // 等待子线程完成选票统计，如果一半以上完成，则继续执行
                semaphore.tryAcquire((int) Math.floor((peerAddress.size() + 1) / 2), 6000, MILLISECONDS);
            } catch (InterruptedException e) {
                log.warn("election task interrupted by main thread");
            }

            // 统计赞同票的数量
            int votes = 0;

            // 获取结果
            for (Future<VoteResult> future : futureList) {
                try {
                    VoteResult result = null;
                    if (future.isDone()) {
                        result = future.get();
                    }
                    if (result == null) {
                        // rpc调用失败或任务超时
                        continue;
                    }
                    if (result.isVoteGranted()) {
                        votes++;
                    } else {
                        // 更新自己的任期
                        long resTerm = result.getTerm();
                        if (resTerm > term) {
                            term = resTerm;
                            status = FOLLOWER;
                        }
                    }
                } catch (Exception e) {
                    log.error("future.get() exception");
                }
            }

            // 如果投票期间有其他服务器发送 appendEntry , 就可能变成 follower
            if (status == FOLLOWER) {
                log.info("election stops with newer term {}", term);
                updatePreElectionTime();
                votedFor = "";
                return;
            }

            // 需要获得超过半数节点的投票
            if (votes * 2 >= peerAddress.size()) {
                votedFor = "";
                status = LEADER;
                // 启动心跳任务
                heartBeatFuture = scheduledExecutorService.scheduleWithFixedDelay(heartBeatTask, 0, heartBeatInterval, TimeUnit.MILLISECONDS);
                // 初始化
                if (leaderInit()) {
                    log.warn("become leader with {} votes", votes + 1);
                } else {
                    // 重新选举
                    votedFor = "";
                    status = FOLLOWER;
                    updatePreElectionTime();
                    log.info("node {} election fail, votes count = {} ", myAddress, votes);
                }
            } else {
                // 重新选举
                votedFor = "";
                status = FOLLOWER;
                updatePreElectionTime();
                log.info("node {} election fail, votes count = {} ", myAddress, votes);
            }

        }
    }

    /**
     * 发送心跳信号，通过线程池执行
     * 如果收到了来自任期号更大的节点的响应，则转为跟随者
     * RPC请求类型为APPEND_ENTRIES
     */
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
                    .entries(null)// 心跳，空日志.
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

    private synchronized Thread getWaitThread() {
        if (waitThreads.size() > 0) {
            return waitThreads.pop();
        }
        return null;
    }

    /**
     * 变为follower时停止心跳任务
     */
    private void stopHeartBeat() {
        if (heartBeatFuture != null) {
            heartBeatFuture.cancel(true);
            heartBeatFuture = null;
        }
    }

    /**
     * 复制日志到follower上，preLog不匹配时自动重试
     */
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

    /**
     * 读取日志复制结果，并返回成功复制的followers的数量
     */
    private int getReplicationResult(List<Future<Boolean>> futureList) {
        int success = 0;
        for (Future<Boolean> future : futureList) {
            if (future.isDone()) {
                try {
                    if (future.get()) {
                        success++;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.error("future.get() error");
                }
            }
        }
        return success;
    }

    /**
     * 获取logEntry的前一个日志，没有前一个日志时返回一个index和term为-1的no-op空日志
     */
    private LogEntry getPreLog(LogEntry logEntry) {
        LogEntry entry = logModule.read(logEntry.getIndex() - 1);

        if (entry == null) {
            log.info("preLog is null, parameter logEntry : {}", logEntry);
            entry = LogEntry.builder().index(-1L).term(-1).command(null).build();
        }
        return entry;
    }


    private void setCommitIndex(long index) {
        stateMachine.setCommit(index);
    }


    private long getCommitIndex() {
        return stateMachine.getCommit();
    }

}
