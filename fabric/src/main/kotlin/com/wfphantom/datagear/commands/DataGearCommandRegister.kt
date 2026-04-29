package com.wfphantom.datagear.commands

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

object DataGearCommandRegister {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> DataGearCommandHelper.registerCommands(dispatcher) }
    }
}