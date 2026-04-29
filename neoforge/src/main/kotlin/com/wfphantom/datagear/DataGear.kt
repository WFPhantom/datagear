package com.wfphantom.datagear

import com.wfphantom.datagear.commands.DataGearCommandRegister
import com.wfphantom.datagear.loader.DataGearResourceLoaderRegister
import net.neoforged.fml.common.Mod

@Mod("datagear")
object DataGear {
    private val logger = DataGearCommon.logger

    init {
        logger.info("Loading DataGear...")

        DataGearResourceLoaderRegister.register()
        DataGearCommandRegister.register()
        DataGearCommon.initialize()

        logger.info("DataGear loaded successfully!")
    }
}