package com.example.pixcase.di

import javax.inject.Qualifier

/** MediaStore 图片集合的 content Uri。注入常量而不是类内引用,保证 Writer 的 JVM 单测可构造。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ImagesContentUri
