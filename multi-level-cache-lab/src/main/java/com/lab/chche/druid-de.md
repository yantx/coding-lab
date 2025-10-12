# Druid 连接池监控指标说明

## 一、连接池基础配置指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_initial_size` | 连接池初始化大小 | Gauge | 初始连接资源分配情况 |
| `druid_min_idle` | 最小空闲连接数 | Gauge | 连接池保底可用资源 |
| `druid_max_active` | 最大活跃连接数 | Gauge | 防止数据库过载的关键阈值 |

## 二、连接池实时状态指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_active_count` | **当前活跃连接数** | Gauge | 实时负载压力核心指标 |
| `druid_active_peak` | 历史活跃连接峰值 | Gauge | 最大并发需求分析 |
| `druid_pooling_count` | 当前池中连接总数（活跃+空闲） | Gauge | 资源利用率评估 |
| `druid_pooling_peak` | 历史连接总数峰值 | Gauge | 资源需求峰值分析 |
| `druid_wait_thread_count` | **等待连接的线程数** | Gauge | 资源竞争瓶颈告警 |

## 三、连接等待性能指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_not_empty_wait_count` | 非空等待次数（池无连接时的等待） | Gauge | 资源短缺频率 |
| `druid_not_empty_wait_millis` | 非空等待总时间（毫秒） | Gauge | 资源申请延迟 |
| `druid_not_empty_thread_count` | 触发非空等待的线程数 | Gauge | 竞争范围分析 |

## 四、连接生命周期指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_logic_connect_count` | 逻辑连接打开次数（应用层请求） | Gauge | 应用访问量 |
| `druid_logic_close_count` | 逻辑连接关闭次数 | Gauge | 连接释放频率 |
| `druid_logic_connect_error_count` | 逻辑连接错误数（如获取超时） | Gauge | 资源不足或配置问题 |
| `druid_physical_connect_count` | **物理连接创建次数** | Gauge | 真实DB连接开销 |
| `druid_physical_close_count` | 物理连接关闭次数 | Gauge | 资源回收频率 |
| `druid_physical_connect_error_count` | 物理连接错误数（如网络故障） | Gauge | 数据库可用性风险 |

## 五、SQL执行统计指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_execute_count` | SQL总执行次数 | Gauge | 系统吞吐量 |
| `druid_error_count` | **SQL执行错误总数** | Gauge | 系统稳定性风险 |
| `druid_execute_query_count` | SELECT查询次数 | Gauge | 读操作压力 |
| `druid_execute_update_count` | UPDATE/INSERT操作次数 | Gauge | 写操作压力 |
| `druid_execute_batch_count` | 批量操作次数 | Gauge | 批处理任务量 |

## 六、事务统计指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_start_transaction_count` | 事务开启次数 | Gauge | 事务处理频率 |
| `druid_commit_count` | 事务提交次数 | Gauge | 正常完成量 |
| `druid_rollback_count` | 事务回滚次数 | Gauge | 异常或业务取消量 |

## 七、PreparedStatement缓存指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_prepared_statement_open_count` | PS打开数量 | Gauge | 预编译语句使用量 |
| `druid_prepared_statement_closed_count` | PS关闭数量 | Gauge | 资源释放情况 |
| `druid_ps_cache_access_count` | 缓存访问总次数 | Gauge | 缓存利用率 |
| `druid_ps_cache_hit_count` | **缓存命中次数** | Gauge | 缓存有效性 |
| `druid_ps_cache_miss_count` | 缓存未命中次数 | Gauge | 缓存优化空间 |

## 八、超时与阈值配置指标
| 指标名称 | 描述 | 类型 | 监控意义 |
|----------|------|------|----------|
| `druid_max_wait` | 获取连接最大等待时间（毫秒） | Gauge | 配置合理性检查 |
| `druid_max_wait_thread_count` | 最大等待线程数阈值 | Gauge | 防线程堆积配置 |
| `druid_login_timeout` | 数据库登录超时时间 | Gauge | 认证延迟风险 |
| `druid_query_timeout` | SQL执行超时阈值 | Gauge | 慢查询防控 |
| `druid_transaction_query_timeout` | 事务内查询超时阈值 | Gauge | 事务级性能控制 |

## 指标采集说明
1. **采集方式**：所有指标均通过 Micrometer 的 `Gauge` 实现
2. **标签信息**：每个指标包含 `pool` 标签标识数据源名称
3. **注册方式**：
```java
Gauge.builder(metric, dataSource, measure)
.description(help)
.tag("pool", dataSource.getName())
.register(registry);
```
## 关键指标告警建议
| 指标 | 告警条件 | 问题类型 |
|------|----------|----------|
| `druid_active_count` | `值 > druid_max_active * 0.8` | 连接池过载 |
| `druid_wait_thread_count` | `值 > 0 持续5分钟` | 连接获取阻塞 |
| `druid_error_count` | `增长率 > 10/分钟` | SQL执行异常 |
| `druid_physical_connect_error_count` | `值 > 0` | 数据库连接故障 |
| `druid_rollback_count` | `占事务总数比例 > 20%` | 事务异常率过高 |

> **指标类型说明**：
> - **Gauge**：瞬时值指标，表示采集时刻的当前值
> - **Counter**：累加值指标（本实现中未使用）
> - **Histogram**：直方图指标（本实现中未使用）
>
> 完整实现代码见 [DruidCollector.java]