"""Production cache selection must not confuse named and intermediary games."""
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_matrix import find_game_jar

class ProductionCacheTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.cache = Path(self.temp.name)

    def jar(self, artifact, version):
        path = self.cache / 'caches/fabric-loom/minecraftMaven/net/minecraft' / artifact / version / (artifact + '.jar')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch()
        return path

    def test_modern_selects_only_production_namespace(self):
        self.jar('minecraft-client', '1.21.11-mapping-v2')
        production = self.jar('minecraft-client-intermediary', '1.21.11-mapping-v2')
        self.assertEqual(production, find_game_jar(self.cache, '1.21.11'))

    def test_legacy_intermediary_in_named_artifact_is_not_production(self):
        self.jar('minecraft-client-legacy-intermediary-1-v2', '1.8.9-mapping-v2')
        production = self.jar('minecraft-client-legacy-intermediary-1-v2-intermediary', '1.8.9-mapping-v2')
        self.assertEqual(production, find_game_jar(self.cache, '1.8.9'))

    def test_wrong_release_is_not_selected(self):
        self.jar('minecraft-client-intermediary', '1.21.1-mapping-v2')
        with self.assertRaisesRegex(ValueError, 'found 0'):
            find_game_jar(self.cache, '1.21')

    def test_ambiguous_mapping_caches_fail_closed(self):
        self.jar('minecraft-client-intermediary', '1.21.11-first-v2')
        self.jar('minecraft-client-intermediary', '1.21.11-second-v2')
        with self.assertRaisesRegex(ValueError, 'found 2'):
            find_game_jar(self.cache, '1.21.11')

    def test_unobfuscated_game_uses_official_production_jar(self):
        path = self.cache / 'caches/fabric-loom/26.1/minecraft-client.jar'
        path.parent.mkdir(parents=True)
        path.touch()
        self.assertEqual(path, find_game_jar(self.cache, '26.1'))
