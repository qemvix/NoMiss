"""Copy shared configuration into an isolated, version-specific build directory."""
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected exactly one source anchor: {old!r}")
    return text.replace(old, new, 1)


def copy_shared(target, output):
    java, resources = output / "java", output / "resources"
    java.mkdir(parents=True, exist_ok=True)
    shutil.copytree(ROOT / "src/main/java", java, dirs_exist_ok=True)
    shutil.copytree(ROOT / "src/main/resources/assets", resources / "assets", dirs_exist_ok=True)
    for path in java.rglob("*.java"):
        text = path.read_text().replace("profiles.getFirst()", "profiles.get(0)")
        # Minecraft 1.19 uses Gson 2.8.9; core configuration must also compile against it.
        text = text.replace("profiles.isEmpty()", "profiles.size() == 0")
        if path.name == "NoMissProfile.java":
            text = re.sub(r'clear\(\); setSlot\(0, WeaponSelection.category\("swords"\)\); setSlot\(1, WeaponSelection.category\("spears"\)\); setSlot\(2, WeaponSelection.category\("maces"\)\);',
                          "clear(); " + " ".join(f'setSlot({i}, WeaponSelection.category("{category}"));'
                                                for i, category in enumerate(target.categories)), text)
        if path.name == "NoMissConfig.java":
            text = text.replace('modpack.setSlot(3, WeaponSelection.category("axes"))',
                                f'modpack.setSlot({len(target.categories)}, WeaponSelection.category("axes"))')
        path.write_text(text)
    return java, resources


def metadata(target, resources, *, legacy=False):
    data = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
    data["version"] = "1.2"
    data["depends"] = {"fabricloader": ">=0.20.1" if legacy else ">=0.19.5",
                       "minecraft": target.minecraft, "java": f">={target.java}"}
    data.pop("jars", None)
    if legacy:
        data["entrypoints"] = {"client": ["com.nomiss.legacy.LegacyClient"]}
        data.pop("suggests", None)
        data["mixins"] = ["nomiss.legacy.mixins.json"]
    else:
        data["suggests"]["modmenu"] = f">={target.modmenu}"
        mixins = json.loads((ROOT / "src/client/resources/nomiss.client.mixins.json").read_text())
        mixins["compatibilityLevel"] = f"JAVA_{target.java}"
        (resources / "nomiss.client.mixins.json").write_text(json.dumps(mixins, indent=2) + "\n")
    (resources / "fabric.mod.json").write_text(json.dumps(data, indent=2) + "\n")
