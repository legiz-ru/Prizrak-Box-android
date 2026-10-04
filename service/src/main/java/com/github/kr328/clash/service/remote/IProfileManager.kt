package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.model.Profile
import com.github.kr328.kaidl.BinderInterface
import java.util.*

@BinderInterface
interface IProfileManager {
    suspend fun create(type: Profile.Type, name: String, source: String = ""): UUID
    suspend fun clone(uuid: UUID): UUID
    suspend fun commit(uuid: UUID, callback: IFetchObserver? = null)
    suspend fun release(uuid: UUID)
    suspend fun delete(uuid: UUID)
    suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String = "")
    suspend fun update(uuid: UUID)

    /**
     * What the panel says about [url] before a profile exists, as JSON (see
     * `ProfileProcessor.parseUrlHeaders`). Asked of this process, not made in the app's,
     * so the import that follows can reuse the very same download.
     */
    suspend fun fetchUrlHeaders(url: String): String
    suspend fun queryByUUID(uuid: UUID): Profile?
    suspend fun queryAll(): List<Profile>
    suspend fun queryActive(): Profile?
    suspend fun setActive(profile: Profile)
}