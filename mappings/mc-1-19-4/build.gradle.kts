tasks {
    remap {
        version.set("1.19.4")
    }
}

dependencies.apply {
    compileOnly("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT:remapped-mojang@jar")
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}
