package com.closecode.tools

import android.content.Context

data class OptionalPack(
    val id: String,
    val label: String,
    val description: String,
    val asset: String,
    val prefix: String,
    val home: String,
    val cachePrefix: String,
    val checks: List<Pair<String, List<String>>> = emptyList(),
)

object OptionalPacks {

    val media = OptionalPack(
        id = "media",
        label = "Media tools",
        description = "ffmpeg, sqlite3 and ImageMagick",
        asset = "shell-media.json",
        prefix = "m",
        home = "hm",
        cachePrefix = "media-tools",
        checks = listOf(
            "ffmpeg" to listOf("-version"),
            "sqlite3" to listOf("-version"),
            "magick" to listOf("-version"),
        ),
    )

    val languages = listOf(
        OptionalPack(
            "go",
            "Go",
            "go build, test and modules",
            "shell-go.json",
            "go",
            "hgo",
            "go-tools",
            listOf("go" to listOf("version")),
        ),
        OptionalPack(
            "rust",
            "Rust",
            "rustc and cargo",
            "shell-rust.json",
            "rs",
            "hrs",
            "rust-tools",
            listOf("rustc" to listOf("--version"), "cargo" to listOf("--version")),
        ),
        OptionalPack(
            "dart",
            "Dart",
            "dart run and pub",
            "shell-dart.json",
            "dart",
            "hdart",
            "dart-tools",
            listOf("dart" to listOf("--version")),
        ),
        OptionalPack(
            "php",
            "PHP",
            "php and composer",
            "shell-php.json",
            "php",
            "hphp",
            "php-tools",
            listOf("php" to listOf("--version")),
        ),
        OptionalPack(
            "ruby",
            "Ruby",
            "ruby and gem",
            "shell-ruby.json",
            "rb",
            "hrb",
            "ruby-tools",
            listOf("ruby" to listOf("--version")),
        ),
        OptionalPack(
            "deno",
            "Deno",
            "deno run and test",
            "shell-deno.json",
            "deno",
            "hdeno",
            "deno-tools",
            listOf("deno" to listOf("--version")),
        ),
        OptionalPack(
            "bun",
            "Bun",
            "bun run and install",
            "shell-bun.json",
            "bun",
            "hbun",
            "bun-tools",
            listOf("bun" to listOf("--version")),
        ),
    )

    val all: List<OptionalPack> = listOf(media) + languages

    fun store(context: Context, pack: OptionalPack): ShellToolsStore = ShellToolsStore(
        context,
        ShellToolsManifest.load(context, pack.asset),
        prefixName = pack.prefix,
        homeName = pack.home,
        label = if (pack.label.endsWith("tools")) pack.label else "${pack.label} tools",
        cachePrefix = pack.cachePrefix,
    )
}
