package com.thoughtworks.archguard.report.domain.repository

import com.thoughtworks.archguard.report.domain.model.ClassSizing
import com.thoughtworks.archguard.report.domain.model.MethodSizing


interface SizingRepository {
    fun getMethodSizingAboveThresholdCount(systemId: Long, threshold: Int): Long

    fun getMethodSizingAboveThreshold(systemId: Long, threshold: Int, limit: Long, offset: Long): List<MethodSizing>
    fun getClassSizingAboveThresholdCount(systemId: Long, threshold: Int): Long
    fun getClassSizingAboveThreshold(systemId: Long, threshold: Int, limit: Long, offset: Long): List<ClassSizing>
}