# DataGear | [![][cf-shield]][cf-link] [![][mr-shield]][mr-link]

**DataGear** is a powerful hot-reloadable library mod for Minecraft 26.1+ that exposes components, flags, and other modifiers as data. It allows developers to modify them using simple JSON datapack files without writing a single line of code.

**Mod, Datapack, and Modpack developers**: Read the [wiki](https://github.com/wfphantom/datagear/wiki) to see how to use DataGear.

## Features

- **Data-Driven items**: Modify any component or modifier such as armor points, toughness, attack damage, speed, durability via datapacks.
- **Live Reload**: Use `/reload` to apply changes instantly.
- **Simple yet powerful JSON engine**: Easy-to-understand data structure. Read the [wiki](https://github.com/wfphantom/datagear/wiki) to see examples.

## Compatibility & API

DataGear is meant to be a simple and lightweight solution, and as so it's designed to work seamlessly with other mods. 
- **Tagging**: DataGear adds some tags to help you target items. See them [here](https://github.com/WFPhantom/datagear/tree/fabric-26.1/src/main/resources/data/datagear/tags/item).
- **Dynamic Discovery**: Dynamically searches for modded modifiers and components, supports most mods out of the box as long as they don't do anything weird.
- **Custom Slots**: Includes a `DataGearPlugin` API for mods to register custom slots (like Curios, Trinkets, or Accessories).

*If you encounter any issues or incompatibilities, please report them [here](https://github.com/wfphantom/datagear/issues).*

Datagear V2 is under development. To see my ever-expanding but never-fulfilled TODO list, click [here](https://github.com/WFPhantom/datagear/blob/fabric-26.1/src/main/kotlin/com/wfphantom/DataGear.kt).

No backports or Neo ports will be happening until v2 is out, after that, the mod will be available for Neoforge and Fabric 26.1+ and MIGHT be backported to 1.21.1 for both loaders. No Forge or 1.20 ports planned.

[mr-shield]: https://img.shields.io/modrinth/dt/CqIH6BQv?style=for-the-badge&logo=modrinth&label=Modrinth&labelColor=black&color=%2300AF5C
[mr-link]: https://modrinth.com/mod/datagear
[cf-shield]: https://img.shields.io/curseforge/dt/1488600?style=for-the-badge&logo=curseforge&label=curseforge&labelColor=black&color=%23F16436
[cf-link]: https://legacy.curseforge.com/minecraft/mc-mods/datagear
