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

package ui.home

import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.Gateway
import repository.BlockaRepository
import ui.utils.cause
import utils.Logger
import java.lang.Exception

class LocationViewModel : ViewModel() {

    private val log = Logger("LocationViewModel")

    private val blocka = BlockaRepository

    private val _locations = MutableLiveData<List<Gateway>>()
    val locations: LiveData<List<Gateway>> = _locations.distinctUntilChanged()

    // 从服务端 获取 Gateway 列表
    fun refreshLocations() {
        viewModelScope.launch {
            try {
                val list = blocka.fetchGateways()

                list.forEach {
                    log.v("Gateway: $it")
                }

                _locations.value = list
            } catch (ex: Exception) {
                Logger.w("Location", "Could not load locations".cause(ex))
            }
        }
    }

}