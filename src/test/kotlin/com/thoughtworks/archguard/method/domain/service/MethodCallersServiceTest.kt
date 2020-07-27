package com.thoughtworks.archguard.method.domain.service

import com.thoughtworks.archguard.method.domain.JMethod
import com.thoughtworks.archguard.method.domain.JMethodRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MethodCallersServiceTest {
    @InjectMockKs
    private var service = MethodCallersService()

    @MockK
    private lateinit var repo: JMethodRepository

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `should get method callers`() {
        //given
        val target = JMethod("id", "method", "clazz", "module", null, emptyList())
        val caller1 = JMethod("1", "caller1", "clazz2", "module", null, emptyList())
        val caller2 = JMethod("2", "caller2", "clazz3", "module", null, emptyList())
        //when
        every { repo.findMethodCallers(target.id) } returns listOf(caller1)
        every { repo.findMethodCallers(caller1.id) } returns listOf(caller2)
        every { repo.findMethodCallers(caller2.id) } returns listOf()
        val result = service.findCallers(target, 2)
        //then
        Assertions.assertThat(result.callers.size).isEqualTo(1)
        Assertions.assertThat(result.callers[0]).isEqualToComparingFieldByField(caller1)
        Assertions.assertThat(result.callers[0].callers.size).isEqualTo(1)
        Assertions.assertThat(result.callers[0].callers[0]).isEqualToComparingFieldByField(caller2)
    }
}