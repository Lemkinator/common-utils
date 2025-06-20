# Keep and adapt kotlin metadata
-keep class kotlin.Metadata

# Do not obfuscate or optimize the SettingsRepository members
-keepclassmembernames class de.lemke.commonutils.data.SettingsRepository {*;}