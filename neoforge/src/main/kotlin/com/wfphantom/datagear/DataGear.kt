package com.wfphantom.datagear

import com.wfphantom.datagear.commands.DataGearCommandRegister
import com.wfphantom.datagear.loader.DataGearResourceLoaderRegister
import net.neoforged.fml.common.Mod

@Mod("datagear")
object DataGear {
    init {
        DataGearCommon.initialize(
            registerResources = { DataGearResourceLoaderRegister.register() },
            registerCommands = { DataGearCommandRegister.register() }
        )
    }
}