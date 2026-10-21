"""Requested release matrix. An entry is a build target, not a compatibility claim."""
from dataclasses import dataclass


@dataclass(frozen=True)
class Target:
    minecraft: str
    java: int
    loader: str
    fabric_api: str = ""
    modmenu: str = ""

    @property
    def categories(self):
        result = ["swords"]
        if version(self.minecraft) >= (1, 21):
            if version(self.minecraft) >= (1, 21, 11):
                result.append("spears")
            result.append("maces")
        return tuple(result)

    @property
    def filename(self):
        return f"nomiss-{self.minecraft}-{self.loader}-1.2.jar"


def version(value):
    return tuple(map(int, value.split(".")))


_MODERN = """
26.1 25 0.155.3+26.1.2 18.0.2
26.1.2 25 0.155.3+26.1.2 18.0.2
1.21.11 21 0.141.6+1.21.11 17.0.1
1.21.10 21 0.138.4+1.21.10 16.0.1
1.21.9 21 0.134.1+1.21.9 16.0.1
1.21.8 21 0.136.1+1.21.8 15.0.2
1.21.7 21 0.129.0+1.21.7 15.0.2
1.21.6 21 0.128.2+1.21.6 15.0.2
1.21.5 21 0.128.2+1.21.5 14.0.2
1.21.4 21 0.119.4+1.21.4 13.0.4
1.21.3 21 0.114.1+1.21.3 12.0.1
1.21.2 21 0.106.1+1.21.2 12.0.1
1.21.1 21 0.116.17+1.21.1 11.0.5
1.21 21 0.102.0+1.21 11.0.5
1.20.6 21 0.100.8+1.20.6 10.0.0
1.20.5 21 0.97.8+1.20.5 10.0.0
1.20.4 17 0.97.3+1.20.4 9.2.0
1.20.3 17 0.91.1+1.20.3 9.0.0
1.20.2 17 0.91.6+1.20.2 8.0.1
1.20.1 17 0.92.12+1.20.1 7.2.2
1.20 17 0.83.0+1.20 7.0.1
1.19.4 17 0.87.2+1.19.4 6.3.1
1.19.3 17 0.76.1+1.19.3 5.1.0
1.19.2 17 0.77.0+1.19.2 4.1.2
1.19.1 17 0.58.5+1.19.1 4.0.5
1.19 17 0.58.0+1.19 4.0.4
"""
MODERN = {
    mc: Target(mc, int(java), "fabric", api, menu)
    for mc, java, api, menu in (line.split() for line in _MODERN.strip().splitlines())
}
LEGACY_VERSIONS = (
    "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
    "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.3", "1.7.2",
    "1.6.4", "1.6.2", "1.6.1",
)
LEGACY = {mc: Target(mc, 17, "legacy-fabric") for mc in LEGACY_VERSIONS}
TARGETS = MODERN | LEGACY


if __name__ == "__main__":
    import json
    from dataclasses import asdict
    print(json.dumps({mc: asdict(target) | {"categories": target.categories, "filename": target.filename}
                      for mc, target in TARGETS.items()}, indent=2))
