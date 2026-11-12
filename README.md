# Apocalypse 2.7.1 (source)

Fabric client mod for Hypixel SkyBlock slayer carries. Minecraft 26.2 (official mappings), Java 25.
Settings menu: `/apocalypse`, commands: `/carry ...`.

Source reconstructed from the bytecode of apocalypse-2.7.1.jar, based on the original carryledger source.

## Party announcements

With `/carry toggle party` on, every counted kill is announced in party chat.

- `/carry partymsg <text>` sets the message, `/carry partymsg` shows it with a preview, `/carry partymsg reset` restores the default `{name}: {done}/{total}`.
- Placeholders: `{name}` `{done}` `{total}` `{left}` `{boss}` `{tier}` `{time}`, e.g. `/carry partymsg {name} {boss} {tier} {done}/{total} ({left} left, {time})`.
- Messages are queued and sent at least `partydelay` apart (default 1000 ms), so several kills at once are not dropped by Hypixel. Change it with `/carry partydelay <ms>` or the "Party msg delay" stepper in `/apocalypse`.

## Build

Java 25 is required. Run `./gradlew build`; the jar is written to `build/libs/`.
