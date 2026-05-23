# QueM Android Drive Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first native Android version of QueM: a local-first queue app with Google Drive-backed metadata sync and attachment references.

**Architecture:** Start with a Kotlin/Jetpack Compose Android app. Keep queue behavior local-first through Room and repositories, isolate Drive API access behind interfaces, and add WorkManager sync after the domain and data layers are testable.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, kotlinx.serialization, Google Play Services Auth, Google Drive API client, JUnit, Kotlin coroutines test, AndroidX test.

---

## File Structure

- `settings.gradle.kts`: Gradle plugin and dependency repository setup.
- `build.gradle.kts`: root plugin declarations.
- `gradle.properties`: AndroidX, Kotlin, and Gradle flags.
- `app/build.gradle.kts`: Android app module configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: app manifest and MainActivity declaration.
- `app/src/main/res/values/styles.xml`: XML theme bridge used by the manifest.
- `app/src/main/java/com/quem/app/MainActivity.kt`: Compose activity entry point.
- `app/src/main/java/com/quem/app/QueMApp.kt`: app-level state holder and navigation shell.
- `app/src/main/java/com/quem/core/model/*.kt`: domain models and enums.
- `app/src/main/java/com/quem/core/time/Clock.kt`: injectable clock.
- `app/src/main/java/com/quem/core/queue/QueueRules.kt`: status transitions and item mutations.
- `app/src/main/java/com/quem/data/local/*.kt`: Room entities, DAOs, database, and mappers.
- `app/src/main/java/com/quem/data/repository/QueueRepository.kt`: local-first repository API and implementation.
- `app/src/main/java/com/quem/data/sync/*.kt`: metadata JSON DTOs, sync manager, sync worker, Drive gateway interfaces.
- `app/src/main/java/com/quem/drive/*.kt`: Google auth and Drive gateway implementation.
- `app/src/main/java/com/quem/ui/*.kt`: Compose screens, UI models, view models, theme.
- `app/src/test/java/com/quem/**/*.kt`: JVM unit tests for rules, serialization, repositories, and sync decisions.
- `app/src/androidTest/java/com/quem/**/*.kt`: Room and Compose UI tests.

## Dependency Baseline

Use stable releases where available and avoid alpha/beta libraries unless required by compatibility. Metadata checked on 2026-05-23:

- Android Gradle Plugin: `9.2.1`
- AGP built-in Kotlin support: `2.3.21`
- Kotlin Compose plugin: `2.3.21`
- Kotlin serialization plugin: `2.3.21`
- KSP Gradle plugin: `2.3.8`
- Compose BOM: `2026.05.01`
- Room: `2.8.4`
- Navigation Compose: `2.9.8`
- Lifecycle Runtime Compose: `2.10.0`
- WorkManager: `2.11.2`
- kotlinx.serialization JSON: `1.11.0`
- Google Play Services Auth: `21.5.1`
- Google API Services Drive: `v3-rev20260428-2.0.0`
- Google API Client Android: `2.9.0`
- Google HTTP Client Gson: `2.1.0`

## Task 1: Scaffold The Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/java/com/quem/app/MainActivity.kt`
- Create: `app/src/main/java/com/quem/app/QueMApp.kt`
- Create: `app/src/main/java/com/quem/ui/theme/Theme.kt`

- [ ] **Step 1: Create Gradle settings**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QueM"
include(":app")
```

- [ ] **Step 2: Create root Gradle build**

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.8" apply false
}
```

- [ ] **Step 3: Create Gradle properties**

Create `gradle.properties`:

```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

- [ ] **Step 4: Create app module build file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.quem.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quem.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.android.gms:play-services-auth:21.5.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20260428-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.9.0")
    implementation("com.google.http-client:google-http-client-gson:2.1.0")

    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
```

- [ ] **Step 5: Create manifest and Compose entry point**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="QueM"
        android:theme="@style/Theme.QueM">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="Theme.QueM" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

Create `app/src/main/java/com/quem/app/MainActivity.kt`:

```kotlin
package com.quem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QueMTheme {
                QueMApp()
            }
        }
    }
}
```

Create `app/src/main/java/com/quem/app/QueMApp.kt`:

```kotlin
package com.quem.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun QueMApp() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("QueM")
    }
}
```

Create `app/src/main/java/com/quem/ui/theme/Theme.kt`:

```kotlin
package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val QueMColorScheme = lightColorScheme()

@Composable
fun QueMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QueMColorScheme,
        content = content
    )
}
```

- [ ] **Step 6: Verify scaffold builds**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit scaffold**

Run:

```powershell
git add settings.gradle.kts build.gradle.kts gradle.properties app
git commit -m "chore: scaffold Android app"
```

## Task 2: Add Domain Models And Queue Rules

**Files:**
- Create: `app/src/main/java/com/quem/core/model/QueueStatus.kt`
- Create: `app/src/main/java/com/quem/core/model/Priority.kt`
- Create: `app/src/main/java/com/quem/core/model/SyncState.kt`
- Create: `app/src/main/java/com/quem/core/model/AttachmentType.kt`
- Create: `app/src/main/java/com/quem/core/model/QueueItem.kt`
- Create: `app/src/main/java/com/quem/core/model/Attachment.kt`
- Create: `app/src/main/java/com/quem/core/model/HistoryEntry.kt`
- Create: `app/src/main/java/com/quem/core/time/Clock.kt`
- Create: `app/src/main/java/com/quem/core/queue/QueueRules.kt`
- Create: `app/src/test/java/com/quem/core/queue/QueueRulesTest.kt`

- [ ] **Step 1: Write failing status transition tests**

Create `app/src/test/java/com/quem/core/queue/QueueRulesTest.kt`:

```kotlin
package com.quem.core.queue

import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class QueueRulesTest {
    private val clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z"))

    @Test
    fun markDoneSetsCompletedAtAndPendingSync() {
        val item = TestItems.queueItem(status = QueueStatus.IN_PROGRESS)
        val result = QueueRules.changeStatus(item, QueueStatus.DONE, clock)

        assertEquals(QueueStatus.DONE, result.status)
        assertEquals(clock.now(), result.completedAt)
        assertNull(result.dismissedAt)
        assertEquals(SyncState.PENDING_SYNC, result.syncState)
    }

    @Test
    fun markDismissedSetsDismissedAtAndPendingSync() {
        val item = TestItems.queueItem(status = QueueStatus.QUEUED)
        val result = QueueRules.changeStatus(item, QueueStatus.DISMISSED, clock)

        assertEquals(QueueStatus.DISMISSED, result.status)
        assertEquals(clock.now(), result.dismissedAt)
        assertNull(result.completedAt)
        assertEquals(SyncState.PENDING_SYNC, result.syncState)
    }

    @Test
    fun reopenFromDoneClearsTerminalTimestamps() {
        val item = TestItems.queueItem(
            status = QueueStatus.DONE,
            completedAt = Instant.parse("2026-05-20T12:00:00Z")
        )
        val result = QueueRules.changeStatus(item, QueueStatus.QUEUED, clock)

        assertEquals(QueueStatus.QUEUED, result.status)
        assertNull(result.completedAt)
        assertNull(result.dismissedAt)
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.core.queue.QueueRulesTest"`

Expected: FAIL because `QueueRules`, models, and `FixedClock` do not exist.

- [ ] **Step 3: Add domain models**

Create `app/src/main/java/com/quem/core/model/QueueStatus.kt`:

```kotlin
package com.quem.core.model

enum class QueueStatus {
    QUEUED,
    IN_PROGRESS,
    DONE,
    DISMISSED
}
```

Create `app/src/main/java/com/quem/core/model/Priority.kt`:

```kotlin
package com.quem.core.model

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}
```

Create `app/src/main/java/com/quem/core/model/SyncState.kt`:

```kotlin
package com.quem.core.model

enum class SyncState {
    SYNCED,
    PENDING_SYNC,
    SYNCING,
    ERROR
}
```

Create `app/src/main/java/com/quem/core/model/AttachmentType.kt`:

```kotlin
package com.quem.core.model

enum class AttachmentType {
    TEXT,
    LINK,
    DRIVE_FILE,
    DRIVE_FOLDER
}
```

Create `app/src/main/java/com/quem/core/model/QueueItem.kt`:

```kotlin
package com.quem.core.model

import java.time.Instant
import java.time.LocalDate

data class QueueItem(
    val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: QueueStatus,
    val priority: Priority?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: SyncState
)
```

Create `app/src/main/java/com/quem/core/model/Attachment.kt`:

```kotlin
package com.quem.core.model

import java.time.Instant

data class Attachment(
    val id: String,
    val queueItemId: String,
    val type: AttachmentType,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState
)
```

Create `app/src/main/java/com/quem/core/model/HistoryEntry.kt`:

```kotlin
package com.quem.core.model

import java.time.Instant

enum class HistoryKind {
    NOTE,
    STATUS_CHANGE,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    EDIT
}

data class HistoryEntry(
    val id: String,
    val queueItemId: String,
    val message: String,
    val kind: HistoryKind,
    val createdAt: Instant
)
```

- [ ] **Step 4: Add clock and rules**

Create `app/src/main/java/com/quem/core/time/Clock.kt`:

```kotlin
package com.quem.core.time

import java.time.Instant

interface Clock {
    fun now(): Instant
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
```

Create `app/src/main/java/com/quem/core/queue/QueueRules.kt`:

```kotlin
package com.quem.core.queue

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.Clock

object QueueRules {
    fun changeStatus(
        item: QueueItem,
        newStatus: QueueStatus,
        clock: Clock
    ): QueueItem {
        val now = clock.now()
        return item.copy(
            status = newStatus,
            updatedAt = now,
            completedAt = if (newStatus == QueueStatus.DONE) now else null,
            dismissedAt = if (newStatus == QueueStatus.DISMISSED) now else null,
            syncState = SyncState.PENDING_SYNC
        )
    }
}
```

Append test helper to `app/src/test/java/com/quem/core/queue/QueueRulesTest.kt`:

```kotlin
private object TestItems {
    fun queueItem(
        status: QueueStatus,
        completedAt: Instant? = null,
        dismissedAt: Instant? = null
    ) = com.quem.core.model.QueueItem(
        id = "item-1",
        driveId = null,
        title = "Test item",
        description = null,
        status = status,
        priority = null,
        dueDate = null,
        tags = emptyList(),
        createdAt = Instant.parse("2026-05-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T12:00:00Z"),
        completedAt = completedAt,
        dismissedAt = dismissedAt,
        syncState = SyncState.SYNCED
    )
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.core.queue.QueueRulesTest"`

Expected: PASS.

- [ ] **Step 6: Commit domain layer**

Run:

```powershell
git add app/src/main/java/com/quem/core app/src/test/java/com/quem/core
git commit -m "feat: add queue domain model"
```

## Task 3: Add Metadata JSON Serialization

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/MetadataSnapshot.kt`
- Create: `app/src/main/java/com/quem/data/sync/MetadataSerializer.kt`
- Create: `app/src/test/java/com/quem/data/sync/MetadataSerializerTest.kt`

- [ ] **Step 1: Write failing serialization tests**

Create `app/src/test/java/com/quem/data/sync/MetadataSerializerTest.kt`:

```kotlin
package com.quem.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSerializerTest {
    @Test
    fun roundTripPreservesDismissedStatusAndOptionalDueDate() {
        val snapshot = MetadataSnapshot(
            version = 1,
            exportedAt = "2026-05-23T12:00:00Z",
            items = listOf(
                MetadataQueueItem(
                    id = "item-1",
                    driveId = null,
                    title = "Cancelled task",
                    description = "No longer relevant",
                    status = "DISMISSED",
                    priority = "HIGH",
                    dueDate = null,
                    tags = listOf("client"),
                    createdAt = "2026-05-20T12:00:00Z",
                    updatedAt = "2026-05-23T12:00:00Z",
                    completedAt = null,
                    dismissedAt = "2026-05-23T12:00:00Z"
                )
            ),
            attachments = emptyList(),
            history = emptyList()
        )

        val json = MetadataSerializer.encode(snapshot)
        val decoded = MetadataSerializer.decode(json)

        assertTrue(json.contains("\"status\":\"DISMISSED\""))
        assertEquals(snapshot, decoded)
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.MetadataSerializerTest"`

Expected: FAIL because metadata classes do not exist.

- [ ] **Step 3: Add serializable DTOs**

Create `app/src/main/java/com/quem/data/sync/MetadataSnapshot.kt`:

```kotlin
package com.quem.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class MetadataSnapshot(
    val version: Int,
    val exportedAt: String,
    val items: List<MetadataQueueItem>,
    val attachments: List<MetadataAttachment>,
    val history: List<MetadataHistoryEntry>
)

@Serializable
data class MetadataQueueItem(
    val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String?,
    val dueDate: String?,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?,
    val dismissedAt: String?
)

@Serializable
data class MetadataAttachment(
    val id: String,
    val queueItemId: String,
    val type: String,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MetadataHistoryEntry(
    val id: String,
    val queueItemId: String,
    val message: String,
    val kind: String,
    val createdAt: String
)
```

- [ ] **Step 4: Add serializer**

Create `app/src/main/java/com/quem/data/sync/MetadataSerializer.kt`:

```kotlin
package com.quem.data.sync

import kotlinx.serialization.json.Json

object MetadataSerializer {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(snapshot: MetadataSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String): MetadataSnapshot = json.decodeFromString(value)
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.MetadataSerializerTest"`

Expected: PASS.

- [ ] **Step 6: Commit metadata serialization**

Run:

```powershell
git add app/src/main/java/com/quem/data/sync app/src/test/java/com/quem/data/sync
git commit -m "feat: add Drive metadata serialization"
```

## Task 4: Add Room Entities, DAO, And Database

**Files:**
- Create: `app/src/main/java/com/quem/data/local/QueueItemEntity.kt`
- Create: `app/src/main/java/com/quem/data/local/AttachmentEntity.kt`
- Create: `app/src/main/java/com/quem/data/local/HistoryEntryEntity.kt`
- Create: `app/src/main/java/com/quem/data/local/Converters.kt`
- Create: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Create: `app/src/main/java/com/quem/data/local/QueMDatabase.kt`
- Create: `app/src/androidTest/java/com/quem/data/local/QueueDaoTest.kt`

- [ ] **Step 1: Write failing DAO test**

Create `app/src/androidTest/java/com/quem/data/local/QueueDaoTest.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class QueueDaoTest {
    private lateinit var db: QueMDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QueMDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.queueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun statusQueryExcludesDismissedFromActiveWhenCallerRequestsQueued() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        dao.upsertItem(QueueItemEntity(id = "queued", driveId = null, title = "Queued", description = null, status = "QUEUED", priority = null, dueDate = null, tags = emptyList(), createdAt = now, updatedAt = now, completedAt = null, dismissedAt = null, syncState = "PENDING_SYNC"))
        dao.upsertItem(QueueItemEntity(id = "dismissed", driveId = null, title = "Dismissed", description = null, status = "DISMISSED", priority = null, dueDate = null, tags = emptyList(), createdAt = now, updatedAt = now, completedAt = null, dismissedAt = now, syncState = "PENDING_SYNC"))

        val queued = dao.observeItemsByStatus("QUEUED").first()

        assertEquals(listOf("queued"), queued.map { it.id })
    }
}
```

- [ ] **Step 2: Run failing DAO test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.data.local.QueueDaoTest`

Expected: FAIL because Room files do not exist.

- [ ] **Step 3: Add Room converters and entities**

Create `app/src/main/java/com/quem/data/local/Converters.kt`:

```kotlin
package com.quem.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToString(value: Instant?): String? = value?.toString()
    @TypeConverter fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun tagsToString(value: List<String>): String = value.joinToString(separator = "\u001F")
    @TypeConverter fun stringToTags(value: String): List<String> = if (value.isBlank()) emptyList() else value.split("\u001F")
}
```

Create `app/src/main/java/com/quem/data/local/QueueItemEntity.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: String
)
```

Create `app/src/main/java/com/quem/data/local/AttachmentEntity.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val type: String,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: String
)
```

Create `app/src/main/java/com/quem/data/local/HistoryEntryEntity.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val message: String,
    val kind: String,
    val createdAt: Instant
)
```

- [ ] **Step 4: Add DAO and database**

Create `app/src/main/java/com/quem/data/local/QueueDao.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items WHERE status = :status ORDER BY updatedAt DESC")
    fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<QueueItemEntity?>

    @Query("SELECT * FROM queue_items WHERE syncState = 'PENDING_SYNC'")
    suspend fun pendingItems(): List<QueueItemEntity>

    @Upsert
    suspend fun upsertItem(item: QueueItemEntity)

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertHistoryEntry(entry: HistoryEntryEntity)

    @Query("SELECT * FROM attachments WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM history_entries WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>>
}
```

Create `app/src/main/java/com/quem/data/local/QueMDatabase.kt`:

```kotlin
package com.quem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [QueueItemEntity::class, AttachmentEntity::class, HistoryEntryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QueMDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
}
```

- [ ] **Step 5: Run DAO test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.data.local.QueueDaoTest`

Expected: PASS.

- [ ] **Step 6: Commit Room layer**

Run:

```powershell
git add app/src/main/java/com/quem/data/local app/src/androidTest/java/com/quem/data/local
git commit -m "feat: add local queue database"
```

## Task 5: Add Local-First Repository

**Files:**
- Create: `app/src/main/java/com/quem/data/local/LocalMappers.kt`
- Create: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Create: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Create: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write repository behavior test**

Create `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`:

```kotlin
package com.quem.data.repository

import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RoomQueueRepositoryTest {
    @Test
    fun createItemCreatesQueuedPendingSyncItem() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )

        val created = repository.createItem(title = "Read contract", description = null)

        assertEquals("item-1", created.id)
        assertEquals(QueueStatus.QUEUED, created.status)
        assertEquals(SyncState.PENDING_SYNC, created.syncState)
        assertEquals(created, dao.items.single())
    }
}
```

- [ ] **Step 2: Run failing repository test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: FAIL because repository and fake DAO are not available.

- [ ] **Step 3: Add mappers**

Create `app/src/main/java/com/quem/data/local/LocalMappers.kt`:

```kotlin
package com.quem.data.local

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.Priority
import com.quem.core.model.SyncState

fun QueueItemEntity.toModel(): QueueItem = QueueItem(
    id = id,
    driveId = driveId,
    title = title,
    description = description,
    status = QueueStatus.valueOf(status),
    priority = priority?.let(Priority::valueOf),
    dueDate = dueDate,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    dismissedAt = dismissedAt,
    syncState = SyncState.valueOf(syncState)
)

fun QueueItem.toEntity(): QueueItemEntity = QueueItemEntity(
    id = id,
    driveId = driveId,
    title = title,
    description = description,
    status = status.name,
    priority = priority?.name,
    dueDate = dueDate,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    dismissedAt = dismissedAt,
    syncState = syncState.name
)
```

- [ ] **Step 4: Add repository interface and implementation**

Create `app/src/main/java/com/quem/data/repository/QueueRepository.kt`:

```kotlin
package com.quem.data.repository

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun observeItems(status: QueueStatus): Flow<List<QueueItem>>
    fun observeItem(id: String): Flow<QueueItem?>
    suspend fun createItem(title: String, description: String?): QueueItem
    suspend fun changeStatus(id: String, status: QueueStatus)
}
```

Create `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`:

```kotlin
package com.quem.data.repository

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.queue.QueueRules
import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toEntity
import com.quem.data.local.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomQueueRepository(
    private val dao: QueueDao,
    private val clock: Clock,
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) : QueueRepository {
    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        dao.observeItemsByStatus(status.name).map { entities -> entities.map { it.toModel() } }

    override fun observeItem(id: String): Flow<QueueItem?> =
        dao.observeItem(id).map { it?.toModel() }

    override suspend fun createItem(title: String, description: String?): QueueItem {
        val now = clock.now()
        val item = QueueItem(
            id = idProvider(),
            driveId = null,
            title = title.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            status = QueueStatus.QUEUED,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )
        dao.upsertItem(item.toEntity())
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus) {
        val item = dao.observeItem(id).first()?.toModel() ?: return
        dao.upsertItem(QueueRules.changeStatus(item, status, clock).toEntity())
    }
}
```

- [ ] **Step 5: Add test fake**

Append to `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`:

```kotlin
private class FakeQueueDao : com.quem.data.local.QueueDao {
    val items = mutableListOf<com.quem.core.model.QueueItem>()

    override fun observeItemsByStatus(status: String) = kotlinx.coroutines.flow.flowOf(
        items.filter { it.status.name == status }.map { it.toEntity() }
    )

    override fun observeItem(id: String) = kotlinx.coroutines.flow.flowOf(
        items.firstOrNull { it.id == id }?.toEntity()
    )

    override suspend fun pendingItems() = items
        .filter { it.syncState == SyncState.PENDING_SYNC }
        .map { it.toEntity() }

    override suspend fun upsertItem(item: com.quem.data.local.QueueItemEntity) {
        items.removeAll { it.id == item.id }
        items.add(item.toModel())
    }

    override suspend fun upsertAttachment(attachment: com.quem.data.local.AttachmentEntity) = Unit
    override suspend fun upsertHistoryEntry(entry: com.quem.data.local.HistoryEntryEntity) = Unit
    override fun observeAttachments(queueItemId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.quem.data.local.AttachmentEntity>())
    override fun observeHistory(queueItemId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.quem.data.local.HistoryEntryEntity>())
}
```

- [ ] **Step 6: Run repository tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: PASS.

- [ ] **Step 7: Commit repository**

Run:

```powershell
git add app/src/main/java/com/quem/data app/src/test/java/com/quem/data
git commit -m "feat: add local-first queue repository"
```

## Task 6: Build Queue UI Shell With Fake Repository

**Files:**
- Create: `app/src/main/java/com/quem/ui/QueueListScreen.kt`
- Create: `app/src/main/java/com/quem/ui/QueueStatusTabs.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Create: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Write Compose UI test**

Create `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quem.core.model.QueueStatus
import org.junit.Rule
import org.junit.Test

class QueueListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsDismissedTabAndQueuedItem() {
        compose.setContent {
            QueueListScreen(
                selectedStatus = QueueStatus.QUEUED,
                items = listOf(QueueListItemUi("item-1", "Read contract", "High", null, "2 attachments")),
                onStatusSelected = {},
                onItemSelected = {},
                onCreateItem = {}
            )
        }

        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("In Progress").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Dismissed").assertIsDisplayed()
        compose.onNodeWithText("Read contract").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run failing UI test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest`

Expected: FAIL because UI files do not exist.

- [ ] **Step 3: Create queue list UI**

Create `app/src/main/java/com/quem/ui/QueueStatusTabs.kt`:

```kotlin
package com.quem.ui

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.quem.core.model.QueueStatus

@Composable
fun QueueStatusTabs(
    selectedStatus: QueueStatus,
    onStatusSelected: (QueueStatus) -> Unit
) {
    val statuses = listOf(
        QueueStatus.QUEUED to "Queued",
        QueueStatus.IN_PROGRESS to "In Progress",
        QueueStatus.DONE to "Done",
        QueueStatus.DISMISSED to "Dismissed"
    )
    TabRow(selectedTabIndex = statuses.indexOfFirst { it.first == selectedStatus }) {
        statuses.forEach { (status, label) ->
            Tab(
                selected = status == selectedStatus,
                onClick = { onStatusSelected(status) },
                text = { Text(label) }
            )
        }
    }
}
```

Create `app/src/main/java/com/quem/ui/QueueListScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

data class QueueListItemUi(
    val id: String,
    val title: String,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachmentSummary: String
)

@Composable
fun QueueListScreen(
    selectedStatus: QueueStatus,
    items: List<QueueListItemUi>,
    onStatusSelected: (QueueStatus) -> Unit,
    onItemSelected: (String) -> Unit,
    onCreateItem: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        QueueStatusTabs(selectedStatus, onStatusSelected)
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("QueM", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onCreateItem) { Text("New") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onItemSelected(item.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(item.priorityLabel, item.dueDateLabel, item.attachmentSummary).joinToString(" | "))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Wire app to fake queue content**

Replace `app/src/main/java/com/quem/app/QueMApp.kt`:

```kotlin
package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quem.core.model.QueueStatus
import com.quem.ui.QueueListItemUi
import com.quem.ui.QueueListScreen

@Composable
fun QueMApp() {
    var selectedStatus by remember { mutableStateOf(QueueStatus.QUEUED) }
    QueueListScreen(
        selectedStatus = selectedStatus,
        items = listOf(
            QueueListItemUi(
                id = "sample-1",
                title = "Client onboarding packet",
                priorityLabel = "High",
                dueDateLabel = null,
                attachmentSummary = "4 attachments"
            )
        ),
        onStatusSelected = { selectedStatus = it },
        onItemSelected = {},
        onCreateItem = {}
    )
}
```

- [ ] **Step 5: Run UI test and build**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest
.\gradlew.bat :app:assembleDebug
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit UI shell**

Run:

```powershell
git add app/src/main/java/com/quem/app app/src/main/java/com/quem/ui app/src/androidTest/java/com/quem/ui
git commit -m "feat: add queue list UI"
```

## Task 7: Add Drive Gateway Interface And Sync Manager

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/DriveGateway.kt`
- Create: `app/src/main/java/com/quem/data/sync/SyncManager.kt`
- Create: `app/src/test/java/com/quem/data/sync/SyncManagerTest.kt`

- [ ] **Step 1: Write sync manager test**

Create `app/src/test/java/com/quem/data/sync/SyncManagerTest.kt`:

```kotlin
package com.quem.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncManagerTest {
    @Test
    fun uploadWritesMetadataToQueMFolder() = runTest {
        val gateway = FakeDriveGateway()
        val manager = SyncManager(gateway)
        val snapshot = MetadataSnapshot(
            version = 1,
            exportedAt = "2026-05-23T12:00:00Z",
            items = emptyList(),
            attachments = emptyList(),
            history = emptyList()
        )

        manager.upload(snapshot)

        assertEquals("QueM", gateway.folderName)
        assertEquals("queue-metadata.json", gateway.fileName)
        assertEquals(snapshot, MetadataSerializer.decode(gateway.content))
    }
}
```

- [ ] **Step 2: Run failing sync test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.SyncManagerTest"`

Expected: FAIL because `DriveGateway` and `SyncManager` do not exist.

- [ ] **Step 3: Add gateway interface and manager**

Create `app/src/main/java/com/quem/data/sync/DriveGateway.kt`:

```kotlin
package com.quem.data.sync

interface DriveGateway {
    suspend fun uploadTextFile(folderName: String, fileName: String, content: String)
    suspend fun downloadTextFile(folderName: String, fileName: String): String?
}
```

Create `app/src/main/java/com/quem/data/sync/SyncManager.kt`:

```kotlin
package com.quem.data.sync

class SyncManager(private val driveGateway: DriveGateway) {
    suspend fun upload(snapshot: MetadataSnapshot) {
        driveGateway.uploadTextFile(
            folderName = "QueM",
            fileName = "queue-metadata.json",
            content = MetadataSerializer.encode(snapshot)
        )
    }

    suspend fun download(): MetadataSnapshot? {
        val content = driveGateway.downloadTextFile("QueM", "queue-metadata.json") ?: return null
        return MetadataSerializer.decode(content)
    }
}
```

- [ ] **Step 4: Add test fake**

Append to `app/src/test/java/com/quem/data/sync/SyncManagerTest.kt`:

```kotlin
private class FakeDriveGateway : DriveGateway {
    lateinit var folderName: String
    lateinit var fileName: String
    lateinit var content: String

    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        this.folderName = folderName
        this.fileName = fileName
        this.content = content
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? = null
}
```

- [ ] **Step 5: Run sync tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.SyncManagerTest"`

Expected: PASS.

- [ ] **Step 6: Commit sync interface**

Run:

```powershell
git add app/src/main/java/com/quem/data/sync app/src/test/java/com/quem/data/sync
git commit -m "feat: add Drive sync boundary"
```

## Task 8: Add WorkManager Sync Worker

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/quem/data/sync/SyncScheduler.kt`
- Create: `app/src/test/java/com/quem/data/sync/SyncSchedulerTest.kt`

- [ ] **Step 1: Write scheduler test**

Create `app/src/test/java/com/quem/data/sync/SyncSchedulerTest.kt`:

```kotlin
package com.quem.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class SyncSchedulerTest {
    @Test
    fun periodicSyncIntervalIsFifteenMinutes() {
        assertEquals(Duration.ofMinutes(15), SyncScheduler.periodicInterval)
    }
}
```

- [ ] **Step 2: Run failing scheduler test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.SyncSchedulerTest"`

Expected: FAIL because `SyncScheduler` does not exist.

- [ ] **Step 3: Add worker and scheduler shell**

Create `app/src/main/java/com/quem/data/sync/SyncWorker.kt`:

```kotlin
package com.quem.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
```

Create `app/src/main/java/com/quem/data/sync/SyncScheduler.kt`:

```kotlin
package com.quem.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

object SyncScheduler {
    val periodicInterval: Duration = Duration.ofMinutes(15)
    private const val PERIODIC_SYNC_NAME = "quem-periodic-sync"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            periodicInterval.toMinutes(),
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
```

- [ ] **Step 4: Run scheduler test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.SyncSchedulerTest"`

Expected: PASS.

- [ ] **Step 5: Commit worker shell**

Run:

```powershell
git add app/src/main/java/com/quem/data/sync app/src/test/java/com/quem/data/sync
git commit -m "feat: schedule background sync"
```

## Task 9: Add Google Auth And Drive Implementation

**Files:**
- Create: `app/src/main/java/com/quem/drive/GoogleAuthClient.kt`
- Create: `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt`
- Create: `app/src/test/java/com/quem/drive/GoogleDriveGatewayTest.kt`

- [ ] **Step 1: Write Drive gateway query test**

Create `app/src/test/java/com/quem/drive/GoogleDriveGatewayTest.kt`:

```kotlin
package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveGatewayTest {
    @Test
    fun metadataFileQueryEscapesFolderAndFileNames() {
        val query = GoogleDriveQueries.fileInFolderQuery(
            folderId = "folder123",
            fileName = "queue-metadata.json"
        )

        assertEquals("'folder123' in parents and name = 'queue-metadata.json' and trashed = false", query)
    }
}
```

- [ ] **Step 2: Run failing Drive test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.GoogleDriveGatewayTest"`

Expected: FAIL because Drive files do not exist.

- [ ] **Step 3: Add auth client shell**

Create `app/src/main/java/com/quem/drive/GoogleAuthClient.kt`:

```kotlin
package com.quem.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

class GoogleAuthClient(private val context: Context) {
    private val driveFileScope = Scope("https://www.googleapis.com/auth/drive.file")

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveFileScope)
            .build()

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
}
```

- [ ] **Step 4: Add Drive gateway implementation shell**

Create `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt`:

```kotlin
package com.quem.drive

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.quem.data.sync.DriveGateway
import java.io.ByteArrayOutputStream

object GoogleDriveQueries {
    fun fileInFolderQuery(folderId: String, fileName: String): String =
        "'${folderId}' in parents and name = '${fileName}' and trashed = false"
}

class GoogleDriveGateway(private val drive: Drive) : DriveGateway {
    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        val folderId = ensureFolder(folderName)
        val existing = findFile(folderId, fileName)
        val mediaContent = com.google.api.client.http.ByteArrayContent.fromString("application/json", content)
        if (existing == null) {
            val metadata = File().setName(fileName).setParents(listOf(folderId))
            drive.files().create(metadata, mediaContent).setFields("id").execute()
        } else {
            drive.files().update(existing.id, null, mediaContent).setFields("id").execute()
        }
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? {
        val folderId = ensureFolder(folderName)
        val file = findFile(folderId, fileName) ?: return null
        val output = ByteArrayOutputStream()
        drive.files().get(file.id).executeMediaAndDownloadTo(output)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun ensureFolder(folderName: String): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
        val existing = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id, name)").execute().files.firstOrNull()
        if (existing != null) return existing.id
        val folder = File().setName(folderName).setMimeType("application/vnd.google-apps.folder")
        return drive.files().create(folder).setFields("id").execute().id
    }

    private fun findFile(folderId: String, fileName: String): File? =
        drive.files().list()
            .setQ(GoogleDriveQueries.fileInFolderQuery(folderId, fileName))
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            .firstOrNull()
}
```

- [ ] **Step 5: Run Drive test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.GoogleDriveGatewayTest"`

Expected: PASS.

- [ ] **Step 6: Commit Drive implementation shell**

Run:

```powershell
git add app/src/main/java/com/quem/drive app/src/test/java/com/quem/drive
git commit -m "feat: add Google Drive gateway"
```

## Task 10: Add Item Detail And Status Actions

**Files:**
- Create: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Create: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

- [ ] **Step 1: Write UI test for Dismissed action**

Create `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ItemDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsDismissActionAndOptionalDueDateEmptyState() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = listOf("contract.pdf"),
                history = listOf("Created item"),
                onDismiss = {},
                onDone = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Read contract").assertIsDisplayed()
        compose.onNodeWithText("No due date").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertIsDisplayed()
        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run failing detail test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: FAIL because detail screen does not exist.

- [ ] **Step 3: Add item detail screen**

Create `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<String>,
    history: List<String>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text(title)
        Text(description ?: "")
        Text(dueDateLabel ?: "No due date")
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
        }
        Text("Attachments")
        attachments.forEach { Text(it) }
        Text("History")
        history.forEach { Text(it) }
    }
}
```

- [ ] **Step 4: Run detail test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: PASS.

- [ ] **Step 5: Commit detail screen**

Run:

```powershell
git add app/src/main/java/com/quem/ui app/src/androidTest/java/com/quem/ui
git commit -m "feat: add item detail status actions"
```

## Task 11: Add Create Item Form

**Files:**
- Create: `app/src/main/java/com/quem/ui/CreateItemScreen.kt`
- Create: `app/src/androidTest/java/com/quem/ui/CreateItemScreenTest.kt`

- [ ] **Step 1: Write UI test**

Create `app/src/androidTest/java/com/quem/ui/CreateItemScreenTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class CreateItemScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsOptionalDueDateLabel() {
        compose.setContent {
            CreateItemScreen(
                onSave = { _, _, _, _ -> },
                onCancel = {}
            )
        }

        compose.onNodeWithText("Title").assertIsDisplayed()
        compose.onNodeWithText("Due date optional").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run failing create form test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.CreateItemScreenTest`

Expected: FAIL because create screen does not exist.

- [ ] **Step 3: Add create form**

Create `app/src/main/java/com/quem/ui/CreateItemScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateItemScreen(
    onSave: (title: String, description: String?, priority: String?, dueDate: String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
        OutlinedTextField(value = priority, onValueChange = { priority = it }, label = { Text("Priority") })
        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due date optional") })
        Button(
            onClick = {
                onSave(
                    title,
                    description.takeIf { it.isNotBlank() },
                    priority.takeIf { it.isNotBlank() },
                    dueDate.takeIf { it.isNotBlank() }
                )
            },
            enabled = title.isNotBlank()
        ) { Text("Save") }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}
```

- [ ] **Step 4: Run create form test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.CreateItemScreenTest`

Expected: PASS.

- [ ] **Step 5: Commit create form**

Run:

```powershell
git add app/src/main/java/com/quem/ui app/src/androidTest/java/com/quem/ui
git commit -m "feat: add create item form"
```

## Task 12: Add Attachment Creation And Display

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/LocalMappers.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Create: `app/src/main/java/com/quem/ui/AttachmentEditor.kt`
- Create: `app/src/androidTest/java/com/quem/ui/AttachmentEditorTest.kt`

- [ ] **Step 1: Write attachment editor UI test**

Create `app/src/androidTest/java/com/quem/ui/AttachmentEditorTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AttachmentEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsAllAttachmentTypes() {
        compose.setContent {
            AttachmentEditor(
                onAddText = {},
                onAddLink = {},
                onAttachDriveFile = {},
                onAttachDriveFolder = {}
            )
        }

        compose.onNodeWithText("Text").assertIsDisplayed()
        compose.onNodeWithText("Link").assertIsDisplayed()
        compose.onNodeWithText("Drive file").assertIsDisplayed()
        compose.onNodeWithText("Drive folder").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run failing attachment UI test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.AttachmentEditorTest`

Expected: FAIL because `AttachmentEditor` does not exist.

- [ ] **Step 3: Add attachment mappers and repository API**

Add to `app/src/main/java/com/quem/data/local/LocalMappers.kt`:

```kotlin
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType

fun AttachmentEntity.toModel(): Attachment = Attachment(
    id = id,
    queueItemId = queueItemId,
    type = AttachmentType.valueOf(type),
    displayName = displayName,
    textContent = textContent,
    url = url,
    driveFileId = driveFileId,
    mimeType = mimeType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = SyncState.valueOf(syncState)
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    queueItemId = queueItemId,
    type = type.name,
    displayName = displayName,
    textContent = textContent,
    url = url,
    driveFileId = driveFileId,
    mimeType = mimeType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState.name
)
```

Add to `QueueRepository`:

```kotlin
fun observeAttachments(queueItemId: String): Flow<List<com.quem.core.model.Attachment>>
suspend fun addTextAttachment(queueItemId: String, title: String, text: String)
suspend fun addLinkAttachment(queueItemId: String, title: String, url: String)
suspend fun addDriveAttachment(queueItemId: String, title: String, driveFileId: String, mimeType: String?, isFolder: Boolean)
```

- [ ] **Step 4: Add repository attachment methods**

Add to `RoomQueueRepository`:

```kotlin
override fun observeAttachments(queueItemId: String) =
    dao.observeAttachments(queueItemId).map { rows -> rows.map { it.toModel() } }

override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) {
    addAttachment(queueItemId, title, com.quem.core.model.AttachmentType.TEXT, text, null, null, null)
}

override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) {
    addAttachment(queueItemId, title, com.quem.core.model.AttachmentType.LINK, null, url, null, null)
}

override suspend fun addDriveAttachment(queueItemId: String, title: String, driveFileId: String, mimeType: String?, isFolder: Boolean) {
    addAttachment(
        queueItemId = queueItemId,
        title = title,
        type = if (isFolder) com.quem.core.model.AttachmentType.DRIVE_FOLDER else com.quem.core.model.AttachmentType.DRIVE_FILE,
        text = null,
        url = null,
        driveFileId = driveFileId,
        mimeType = mimeType
    )
}

private suspend fun addAttachment(
    queueItemId: String,
    title: String,
    type: com.quem.core.model.AttachmentType,
    text: String?,
    url: String?,
    driveFileId: String?,
    mimeType: String?
) {
    val now = clock.now()
    val attachment = com.quem.core.model.Attachment(
        id = idProvider(),
        queueItemId = queueItemId,
        type = type,
        displayName = title.trim(),
        textContent = text,
        url = url,
        driveFileId = driveFileId,
        mimeType = mimeType,
        createdAt = now,
        updatedAt = now,
        syncState = com.quem.core.model.SyncState.PENDING_SYNC
    )
    dao.upsertAttachment(attachment.toEntity())
}
```

- [ ] **Step 5: Add attachment editor**

Create `app/src/main/java/com/quem/ui/AttachmentEditor.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AttachmentEditor(
    onAddText: () -> Unit,
    onAddLink: () -> Unit,
    onAttachDriveFile: () -> Unit,
    onAttachDriveFolder: () -> Unit
) {
    Row {
        OutlinedButton(onClick = onAddText) { Text("Text") }
        OutlinedButton(onClick = onAddLink) { Text("Link") }
        OutlinedButton(onClick = onAttachDriveFile) { Text("Drive file") }
        OutlinedButton(onClick = onAttachDriveFolder) { Text("Drive folder") }
    }
}
```

- [ ] **Step 6: Run attachment test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.AttachmentEditorTest`

Expected: PASS.

- [ ] **Step 7: Commit attachments**

Run:

```powershell
git add app/src/main/java/com/quem/data app/src/main/java/com/quem/ui app/src/androidTest/java/com/quem/ui
git commit -m "feat: add queue attachments"
```

## Task 13: Add Archive Search And Filtering

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Create: `app/src/test/java/com/quem/data/repository/QueueSearchTest.kt`

- [ ] **Step 1: Write search behavior test**

Create `app/src/test/java/com/quem/data/repository/QueueSearchTest.kt`:

```kotlin
package com.quem.data.repository

import com.quem.core.model.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSearchTest {
    @Test
    fun archiveStatusesAreDoneAndDismissed() {
        assertEquals(listOf(QueueStatus.DONE, QueueStatus.DISMISSED), QueueFilters.archiveStatuses)
    }
}
```

- [ ] **Step 2: Run failing search test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.QueueSearchTest"`

Expected: FAIL because `QueueFilters` does not exist.

- [ ] **Step 3: Add filters and DAO search**

Create `app/src/main/java/com/quem/data/repository/QueueFilters.kt`:

```kotlin
package com.quem.data.repository

import com.quem.core.model.QueueStatus

object QueueFilters {
    val archiveStatuses = listOf(QueueStatus.DONE, QueueStatus.DISMISSED)
}
```

Add to `QueueDao`:

```kotlin
@Query("""
    SELECT * FROM queue_items
    WHERE status IN (:statuses)
    AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
    ORDER BY updatedAt DESC
""")
fun searchItems(statuses: List<String>, query: String): Flow<List<QueueItemEntity>>
```

Add to `QueueRepository`:

```kotlin
fun searchArchive(query: String): Flow<List<QueueItem>>
```

Add to `RoomQueueRepository`:

```kotlin
override fun searchArchive(query: String): Flow<List<QueueItem>> =
    dao.searchItems(QueueFilters.archiveStatuses.map { it.name }, query.trim())
        .map { rows -> rows.map { it.toModel() } }
```

- [ ] **Step 4: Run search test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.QueueSearchTest"`

Expected: PASS.

- [ ] **Step 5: Commit archive search**

Run:

```powershell
git add app/src/main/java/com/quem/data app/src/test/java/com/quem/data
git commit -m "feat: add archive search"
```

## Task 14: Add Settings And Sync Status UI

**Files:**
- Create: `app/src/main/java/com/quem/ui/SettingsScreen.kt`
- Create: `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`

- [ ] **Step 1: Write settings UI test**

Create `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsAccountAndManualSyncControls() {
        compose.setContent {
            SettingsScreen(
                accountEmail = "user@example.com",
                syncStatus = "Last synced just now",
                onManualSync = {},
                onDisconnect = {}
            )
        }

        compose.onNodeWithText("user@example.com").assertIsDisplayed()
        compose.onNodeWithText("Last synced just now").assertIsDisplayed()
        compose.onNodeWithText("Sync now").assertIsDisplayed()
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run failing settings test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.SettingsScreenTest`

Expected: FAIL because settings screen does not exist.

- [ ] **Step 3: Add settings screen**

Create `app/src/main/java/com/quem/ui/SettingsScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    accountEmail: String?,
    syncStatus: String,
    onManualSync: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(accountEmail ?: "Not signed in")
        Text(syncStatus)
        Button(onClick = onManualSync) { Text("Sync now") }
        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
    }
}
```

- [ ] **Step 4: Run settings test**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.SettingsScreenTest`

Expected: PASS.

- [ ] **Step 5: Commit settings**

Run:

```powershell
git add app/src/main/java/com/quem/ui app/src/androidTest/java/com/quem/ui
git commit -m "feat: add settings sync controls"
```

## Task 15: Wire App Dependencies And Metadata Export

**Files:**
- Create: `app/src/main/java/com/quem/app/QueMApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/quem/data/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/quem/data/sync/MetadataExporter.kt`
- Create: `app/src/test/java/com/quem/data/sync/MetadataExporterTest.kt`

- [ ] **Step 1: Write metadata exporter test**

Create `app/src/test/java/com/quem/data/sync/MetadataExporterTest.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataExporterTest {
    @Test
    fun mapsDismissedItemStatusToMetadata() {
        val item = ExportableQueueItem(id = "item-1", title = "Old task", status = QueueStatus.DISMISSED.name)
        val snapshot = MetadataExporter.export(
            exportedAt = "2026-05-23T12:00:00Z",
            items = listOf(item),
            attachments = emptyList(),
            history = emptyList()
        )

        assertEquals("DISMISSED", snapshot.items.single().status)
    }
}
```

- [ ] **Step 2: Run failing exporter test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.MetadataExporterTest"`

Expected: FAIL because exporter types do not exist.

- [ ] **Step 3: Add metadata exporter**

Create `app/src/main/java/com/quem/data/sync/MetadataExporter.kt`:

```kotlin
package com.quem.data.sync

data class ExportableQueueItem(val id: String, val title: String, val status: String)

object MetadataExporter {
    fun export(
        exportedAt: String,
        items: List<ExportableQueueItem>,
        attachments: List<MetadataAttachment>,
        history: List<MetadataHistoryEntry>
    ): MetadataSnapshot = MetadataSnapshot(
        version = 1,
        exportedAt = exportedAt,
        items = items.map {
            MetadataQueueItem(
                id = it.id,
                driveId = null,
                title = it.title,
                description = null,
                status = it.status,
                priority = null,
                dueDate = null,
                tags = emptyList(),
                createdAt = exportedAt,
                updatedAt = exportedAt,
                completedAt = null,
                dismissedAt = if (it.status == "DISMISSED") exportedAt else null
            )
        },
        attachments = attachments,
        history = history
    )
}
```

- [ ] **Step 4: Add application shell**

Create `app/src/main/java/com/quem/app/QueMApplication.kt`:

```kotlin
package com.quem.app

import android.app.Application
import com.quem.data.sync.SyncScheduler

class QueMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
    }
}
```

Add `android:name=".QueMApplication"` to the `<application>` element in `app/src/main/AndroidManifest.xml`:

```xml
<application
    android:name=".QueMApplication"
    android:allowBackup="true"
    android:label="QueM"
    android:theme="@style/Theme.QueM">
```

- [ ] **Step 5: Keep sync worker successful until dependency injection is added**

Keep `SyncWorker.doWork()` as:

```kotlin
override suspend fun doWork(): Result {
    return Result.success()
}
```

This preserves scheduled sync behavior while app-level dependency injection is added in the next implementation slice.

- [ ] **Step 6: Run exporter test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.sync.MetadataExporterTest"`

Expected: PASS.

- [ ] **Step 7: Commit app wiring**

Run:

```powershell
git add app/src/main
git commit -m "feat: wire app sync shell"
```

## Task 16: Final Verification

**Files:**
- Modify as needed only to fix failures from the verification commands.

- [ ] **Step 1: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Android tests**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build debug APK**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit verification fixes**

If any files changed during final verification, run:

```powershell
git add app
git commit -m "test: stabilize QueM first build"
```

If no files changed, run:

```powershell
git status --short
```

Expected: no output.
