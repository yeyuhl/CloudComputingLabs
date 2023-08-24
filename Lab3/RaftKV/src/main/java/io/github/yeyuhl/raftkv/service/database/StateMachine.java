package io.github.yeyuhl.raftkv.service.database;

import io.github.yeyuhl.raftkv.service.constant.Command;
import io.github.yeyuhl.raftkv.service.log.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import javax.swing.text.html.Option;
import java.io.File;
import java.util.Arrays;

/**
 * 基于RocksDB实现的状态机
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Slf4j
public class StateMachine {
    /**
     * database的存储路径
     */
    private String dbPath;
    /**
     * stateMachine的存储路径
     */
    private String stateMachinePath;
    /**
     * 获取commit index的key值
     */
    public final static byte[] COMMIT_INDEX_KEY = "COMMIT_INDEX".getBytes();
    public RocksDB rocksDB;

    private StateMachine() {
        // 将dbPath和stateMachinePath初始化，其中dbPath为./rocksDB-raft/端口号，stateMachinePath为dbPath/stateMachine
        dbPath = "./rocksDB-raft/" + System.getProperty("server.port");
        stateMachinePath = dbPath + "/stateMachine";
        // 加载RocksDB的库
        RocksDB.loadLibrary();

        // 为stateMachinePath创建目录
        File file = new File(stateMachinePath);
        boolean success = false;
        // 检查stateMachinePath目录是否存在，不存在则创建
        if (!file.exists()) {
            success = file.mkdirs();
        }
        // 如果创建成功则打印日志
        if (success) {
            log.warn("make a new dir: " + stateMachinePath);
        }

        Options options = new Options();
        // Options对象的createIfMissing属性设置为true，即告诉RocksDB在目录不存在时创建stateMachinePath目录
        options.setCreateIfMissing(true);
        try {
            // 尝试在stateMachinePath目录中打开RocksDB数据库
            rocksDB = RocksDB.open(options, stateMachinePath);
        } catch (RocksDBException e) {
            log.error("open rocksDB error: " + e.getMessage());
        }
    }

    /**
     * 获取StateMachine的单例
     */
    public static StateMachine getInstance() {
        return StateMachineLazyHolder.INSTANCE;
    }

    /**
     * 使用懒加载的方式创建StateMachine的单例，即在第一次调用getInstance()方法时才会创建StateMachine的实例
     */
    private static class StateMachineLazyHolder {
        private static final StateMachine INSTANCE = new StateMachine();
    }

    /**
     * 关闭RocksDB
     */
    public void destroy() throws Throwable {
        rocksDB.close();
        log.info("destroy success");
    }

    /**
     * 获取指定key的value
     */
    public String get(String key) {
        try {
            byte[] bytes = rocksDB.get(key.getBytes());
            if (bytes != null) {
                return new String(bytes);
            }
        } catch (RocksDBException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * 将指定的key-value对存入RocksDB
     */
    public void put(String key, String value) {
        try {
            rocksDB.put(key.getBytes(), value.getBytes());
        } catch (RocksDBException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 删除指定的key
     * 其中String... key表示可变长度参数，即可以传入多个key
     */
    public void delete(String... key) {
        try {
            for (String s : key) {
                rocksDB.delete(s.getBytes());
            }
        } catch (RocksDBException e) {
            log.error(e.getMessage());
        }
    }

    public synchronized void apply(LogEntry logEntry) {
        Command command = logEntry.getCommand();
        if (command == null) {
            // no-op
            return;
        }
        switch (command.getType()) {
            case PUT:
                put(command.getKey(), command.getValue());
                break;
            case DELETE:
                delete(command.getKey());
                break;
            default:
                break;
        }
        // 记录请求id，避免重复操作
        put(logEntry.getRequestId(), "1");
    }

    /**
     * 获取最后一个已提交的日志的index，没有已提交日志时返回-1
     */
    public synchronized Long getCommit() {
        byte[] lastCommitIndex = null;
        try {
            lastCommitIndex = rocksDB.get(COMMIT_INDEX_KEY);
        } catch (RocksDBException e) {
            log.error(e.getMessage());
        }
        if (lastCommitIndex == null) {
            lastCommitIndex = "-1".getBytes();
        }
        return Long.valueOf(new String(lastCommitIndex));
    }

    /**
     * 修改commitIndex的接口（持久化）
     */
    public synchronized void setCommit(Long index) {
        try {
            rocksDB.put(COMMIT_INDEX_KEY, index.toString().getBytes());
        } catch (RocksDBException e) {
            log.error(e.getMessage());
        }
    }
}
