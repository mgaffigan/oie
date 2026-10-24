import tempfile
import unittest
from pathlib import Path

import manifest
import split


class Expand(unittest.TestCase):
    def render(self, template, params):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d, 't.gradle')
            f.write_text(template)
            return split.expand(f, params)

    def test_list_repeats_its_line(self):
        self.assertEqual(self.render("a ${x}\nb\n", {'x': ['1', '2']}), "a 1\na 2\nb\n")

    def test_line_with_a_missing_parameter_is_left_out(self):
        self.assertEqual(self.render("a ${x}\nb ${y}\n", {'y': 'v'}), "b v\n")

    def test_unused_parameter_is_an_error(self):
        with self.assertRaises(SystemExit):
            self.render("a ${x}\n", {'x': 'v', 'typo': 'v'})


class PlanMoves(unittest.TestCase):
    def plan(self, plugins, moves=()):
        with tempfile.TemporaryDirectory() as d:
            repo = Path(d).resolve()
            for f in ('server/src/main/java/p/A.java', 'server/src/main/java/p/q/B.java'):
                (repo / f).parent.mkdir(parents=True, exist_ok=True)
                (repo / f).write_text('')
            manifest.MOVES, manifest.PLUGINS = list(moves), plugins
            return {k.relative_to(repo).as_posix(): v.relative_to(repo).as_posix() for k, v in split.plan_moves(repo).items()}

    def test_tier_destination_drops_the_module_directory(self):
        moves = self.plan({'foo': {'moves': [('server/src/main/java/p', 'server')]}})
        self.assertEqual(moves['server/src/main/java/p/q/B.java'], 'plugins/foo/server/src/main/java/p/q/B.java')

    def test_a_file_cannot_move_twice(self):
        with self.assertRaises(SystemExit):
            self.plan({'foo': {'moves': [('server/src/main/java/p', 'server'),
                                         ('server/src/main/java/p/q', 'common')]}})


if __name__ == '__main__':
    unittest.main()
