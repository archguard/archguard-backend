package com.thoughtworks.archguard.architecture.infrastructure

import com.thoughtworks.archguard.architecture.domain.repository.ArchSystemPO
import com.thoughtworks.archguard.architecture.domain.repository.ArchSystemRepository
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.springframework.stereotype.Repository

@Repository
class ArchSystemRepositoryImpl : ArchSystemRepository {

    override fun getArchSystem(id: String): ArchSystemPO {
        TODO("Not yet implemented")
    }

    @Transaction
    override fun createArchSystem(archSystemPO: ArchSystemPO): String {
        TODO("Not yet implemented")
    }
}
