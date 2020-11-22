/*
 * This file is part of Blokada.
 *
 * Blokada is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Blokada is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Blokada.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright © 2020 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package ui

import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.*
import model.Defaults
import engine.EngineService
import service.PersistenceService
import ui.utils.cause
import utils.Logger
import java.lang.Exception
import org.blokada.R
import repository.DnsDataSource

class SettingsViewModel : ViewModel() {

    private val log = Logger("Settings")
    private val persistence = PersistenceService
    private val engine = EngineService

    private val _dnsEntries = MutableLiveData<DnsWrapper>()
    val dnsEntries: LiveData<List<Pair<DnsId, Dns>>> = _dnsEntries.map { entry ->
        entry.value.map { it.id to it }
    }

    private val _localConfig = MutableLiveData<LocalConfig>()
    val localConfig = _localConfig.distinctUntilChanged()

    val selectedDns = localConfig.map { it.dnsChoice }

    private val _syncableConfig = MutableLiveData<SyncableConfig>()
    val syncableConfig = _syncableConfig

    init {
        // 本地的配置
        _localConfig.value = persistence.load(LocalConfig::class)

        // ???
        _syncableConfig.value = persistence.load(SyncableConfig::class)

        //
        _dnsEntries.value = persistence.load(DnsWrapper::class)
        log.v("Config: ${_localConfig.value}")
    }

    // 切换 DNS
    fun setSelectedDns(id: DnsId) {
        _localConfig.value?.let { current ->
            if (id != current.dnsChoice) {
                // 新的id  不等于 当前已经存储的id
                viewModelScope.launch {
                    try {
                        log.v("Changing selected DNS: $id")
                        // 根据 dnsId 从 dns列表中查找
                        val dns = _dnsEntries.value?.value?.first { it.id == id }
                            ?: throw BlokadaException("Unknown DNS")

                        // 更新 engin中的dns
                        engine.changeDns(dns, dnsForPlusMode = decideDnsForPlusMode(dns))

                        val new = current.copy(dnsChoice = id)
                        persistence.save(new)

                        _localConfig.value = new
                    } catch (ex: Exception) {
                        log.e("Could not change dns to $id".cause(ex))

                        // Notify the listener to reset its value to what it was
                        viewModelScope.launch {
                            delay(1000)
                            _localConfig.value = current
                        }
                    }
                }
            }
        }
    }

    // 设置 是否 使用 BlockaDns 在 plus 模式中
    fun setUseBlockaDnsInPlusMode(use: Boolean) {
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                try {
                    log.v("Changing use Blocka DNS in Plus mode: $use")
                    val new = current.copy(useBlockaDnsInPlusMode = use)
                    engine.changeDns(getCurrentDns(), dnsForPlusMode = decideDnsForPlusMode(useBlockaDnsInPlusMode = use))
                    persistence.save(new)
                    _localConfig.value = new
                } catch (ex: Exception) {
                    log.e("Failed changing setting".cause(ex))

                    // Notify the listener to reset its value to what it was
                    viewModelScope.launch {
                        delay(1000)
                        _localConfig.value = current
                    }
                }
            }
        }
    }

    // 给 SyncableConfig 设置 不是 第一次 运行
    fun setFirstTimeSeen() {
        log.v("Marking first time as seen")
        _syncableConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(notFirstRun = true)
                persistence.save(new)
                _syncableConfig.value = new
            }
        }
    }

    // 设置 已经 给应用 评过分了？？？
    fun setRatedApp() {
        log.v("Marking app as rated")
        _syncableConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(rated = true)
                persistence.save(new)
                _syncableConfig.value = new
            }
        }
    }

    //
    fun getCurrentDns(): Dns {
        val current = _dnsEntries.value?.let { entries ->

            _localConfig.value?.let { localConfig ->

                // 从 dns列表中 找到 第一个， localConfig中保存的 dns
                entries.value.firstOrNull { localConfig.dnsChoice == it.id }
                    ?: run {
                        // 如果 localConfig 中未保存，或未找到 匹配的DNS

                        log.w("Currently selected DNS does not exist, resetting to default")

                        // copy出一份新的 localConfig，将默认的dns保存到里面
                        val newLocalConfig = localConfig.copy(dnsChoice = Defaults.localConfig().dnsChoice)

                        // 更新本地的 LocalConfig
                        persistence.save(newLocalConfig)
                        viewModelScope.launch {
                            _localConfig.value = newLocalConfig
                        }

                        // 返回 dns对象
                        entries.value.first { newLocalConfig.dnsChoice == it.id }
                    }
            }
        }

        return current ?: throw BlokadaException("Accessed getCurrentDns() before loaded")
    }

    // 这个方法是用来 判断，是 使用 参数中的dns 还是 BlockaDns
    fun decideDnsForPlusMode(dns: Dns? = null, useBlockaDnsInPlusMode: Boolean? = null): Dns {
        val d = dns ?: getCurrentDns()

        // 是否 使用 useBlockaDns ，默认是true
        val u = useBlockaDnsInPlusMode ?: _localConfig.value?.useBlockaDnsInPlusMode ?: true

        //
        return if (u) DnsDataSource.blocka else d
    }

    fun getUseChromeTabs(): Boolean {
        return _localConfig.value?.useChromeTabs ?: false
    }

    fun setUseChromeTabs(use: Boolean) {
        log.v("Switching the use of Chrome Tabs: $use")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(useChromeTabs = use)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun getTheme(): Int? {
        return _localConfig.value?.let {
            when (it.themeName) {
                THEME_RETRO_KEY -> R.style.Theme_Blokada_Retro
                else -> when (it.useDarkTheme) {
                    true -> R.style.Theme_Blokada_Dark
                    false -> R.style.Theme_Blokada_Light
                    else -> null
                }
            }
        }
    }

    fun setUseDarkTheme(useDarkTheme: Boolean?) {
        log.v("Setting useDarkTheme: $useDarkTheme")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(
                    useDarkTheme = useDarkTheme,
                    themeName = null
                )
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun setUseTheme(name: String) {
        log.v("Setting useTheme: $name")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(
                    useDarkTheme = null,
                    themeName = name
                )
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun getLocale(): String? {
        return _localConfig.value?.locale
    }

    fun setLocale(locale: String?) {
        log.v("Setting locale: $locale")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(locale = locale)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun getIpv6(): Boolean {
        return _localConfig.value?.ipv6 ?: false
    }

    fun setIpv6(ipv6: Boolean) {
        log.v("Setting ipv6: $ipv6")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(ipv6 = ipv6)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun setUseBackup(backup: Boolean) {
        log.v("Setting use cloud backup: $backup")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(backup = backup)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun setUseDnsOverHttps(doh: Boolean) {
        log.v("Setting use DNS over HTTPS: $doh")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(useDnsOverHttps = doh)
                persistence.save(new)
                engine.restart()
                _localConfig.value = new
            }
        }
    }

    fun setEscaped(escaped: Boolean) {
        log.v("Setting escaped: $escaped")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(escaped = escaped)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun setUseForegroundService(use: Boolean) {
        log.v("Setting use Foreground Service: $use")
        _localConfig.value?.let { current ->
            viewModelScope.launch {
                val new = current.copy(useForegroundService = use)
                persistence.save(new)
                _localConfig.value = new
            }
        }
    }

    fun getUseForegroundService(): Boolean {
        return _localConfig.value?.useForegroundService ?: false
    }

}

const val THEME_RETRO_KEY = "retro"
const val THEME_RETRO_NAME = "Retro"