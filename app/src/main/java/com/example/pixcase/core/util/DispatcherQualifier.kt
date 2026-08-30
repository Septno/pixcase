package com.example.pixcase.core.util

import javax.inject.Qualifier

/** 标记 IO 派发器;由 AppModule 提供,用于磁盘/数据库/Cursor 等阻塞操作。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** 标记 CPU 派发器;由 AppModule 提供,用于排序/过滤等计算密集操作。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * 标记 Application 级协程作用域;SupervisorJob + DefaultDispatcher,
 * 由 AppModule 以 @Singleton 提供;用于 Pager.cachedIn 等需要跨 ViewModel 生命周期的缓存。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
