### Druid 连接池核心监控指标说明
**指标分类** | **指标名称** | **数据类型** | **含义说明**  
---|---|---|---  
**连接池状态** | `ActiveCount` | 瞬时值 | 当前正在被使用的活跃连接数 [1,4](@ref)  
| `PoolingCount` | 瞬时值 | 当前连接池中空闲连接数 [4,5](@ref)  
| `ActivePeak` | 峰值 | 历史活跃连接数的最大值 [4,6](@ref)  
| `MaxActive` | 配置值 | 连接池允许的最大活跃连接数 [1,3](@ref)  
| `WaitThreadCount` | 瞬时值 | **关键预警**：当前等待获取连接的线程数（>0 表示连接不足）[4,5](@ref)  
| `NotEmptyWaitCount` | 📊 累计值 | 因连接不足导致请求阻塞的总次数 [5,6](@ref)  
**SQL 执行** | `ExecuteCount` | 📊 累计值 | SQL 语句总执行次数 [1,4](@ref)  
| `SlowQueries` | 📊 累计值 | 执行时间超过阈值的慢查询数量（需配置 `slow-sql-millis`）[1,6](@ref)  
| `ConnectionHoldTimeHistogram` | 分布直方图 | 连接持有时间分布（区间：0-1ms, 1-10ms, 10-100ms, ...）[5,6](@ref)  
| `PSCacheHitCount` | 📊 累计值 | PreparedStatement 缓存命中次数 [4,6](@ref)  
**事务与错误** | `TransactionHistogram` | 分布直方图 | 事务执行时间分布（区间：0-10ms, 10-100ms, ...）[5,6](@ref)  
| `ErrorCount` | 📊 累计值 | SQL 执行错误总数 [1,3](@ref)  
| `PhysicalConnectErrorCount` | 📊 累计值 | 物理连接建立失败次数（网络/权限问题）[4,6](@ref)  
**资源与性能** | `BlobOpenCount`/`ClobOpenCount` | 📊 累计值 | 大对象（Blob/Clob）打开次数 [4,6](@ref)  
| `KeepAliveCheckCount` | 📊 累计值 | 空闲连接保活检测次数 [4](@ref)  
| `RecycleErrorCount` | 📊 累计值 | 连接回收时发生的错误数 [4](@ref)  

### 关键监控场景建议
1. **连接池过载预警**
    - 当 `WaitThreadCount > 0` 或 `NotEmptyWaitCount` 突增时，需检查 `MaxActive` 是否过小或存在连接泄漏 [3,5](@ref)。
      - 示例配置：
          ```yaml
          metrics:
              - name: WaitThreadCount
              threshold: 0
              alert: "连接池过载：等待线程数 > 0"
          druid:
            max-active: 50 # 根据数据库负载动态调整
            max-wait: 2000 # 连接获取最大等待时间（毫秒）
        ```
        
2. **慢查询监控**
    2. **慢 SQL 优化**
- 启用慢 SQL 日志（`slow-sql-millis: 2000`），结合 `SlowQueries` 定位需优化的 SQL [6](@ref)。
- 通过 `ConnectionHoldTimeHistogram` 分析连接占用时间分布，识别长时间持有连接的问题 [5](@ref)。

3. **连接泄漏检测**
- 开启 `removeAbandoned: true` 和 `logAbandoned: true`，自动回收超时未归还的连接并记录堆栈 [4](@ref)。
- 监控 `LogicConnectCount` 与 `LogicCloseCount` 差值，若持续增长可能存在泄漏 [4](@ref)。

> 💡 **数据来源说明**：
> - **瞬时值**：统计时刻的实时快照（如 `ActiveCount`）。
> - **累计值**：自应用启动以来的累计总量（如 `ExecuteCount`）。
> - **峰值**：历史运行过程中的最大值（如 `ActivePeak`）。  
    > 完整参数见 [Druid 官方文档](https://github.com/alibaba/druid/wiki) [4,6](@ref)。
