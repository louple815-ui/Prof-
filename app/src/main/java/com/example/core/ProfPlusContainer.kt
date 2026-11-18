package com.example.core

import android.content.Context
import com.example.R
import com.example.data.local.ProfPlusDatabase
import com.example.data.repository.AdminRepository
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.DebateRepository
import com.example.data.repository.EstablishmentRepository
import com.example.data.repository.FeedRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.ResourceRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore

class ProfPlusContainer(context: Context) {
    private val appContext = context.applicationContext
    val databaseId: String = appContext.getString(R.string.firestore_database_id)
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(databaseId)
    val roomDb: ProfPlusDatabase = ProfPlusDatabase.getInstance(appContext)
    val preferencesManager = PreferencesManager(appContext)
    val networkMonitor = NetworkMonitor(appContext)

    val userRepository = UserRepository(firestore)
    val feedRepository = FeedRepository(firestore)
    val debateRepository = DebateRepository(firestore)
    val groupRepository = GroupRepository(firestore)
    val resourceRepository = ResourceRepository(firestore, roomDb.downloadedResourceDao())
    val messagingRepository = MessagingRepository(firestore)
    val establishmentRepository = EstablishmentRepository(firestore)
    val notificationRepository = NotificationRepository(firestore)
    val adminRepository = AdminRepository(firestore)
    val aiAssistantRepository = AiAssistantRepository()
}
