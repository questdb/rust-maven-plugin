#!/usr/bin/env python3

import argparse
import pathlib
import difflib


# Running this script will also update the version number in this file.
# Do not edit manually.
CURRENT_VERSION = '1.0.0-SNAPSHOT'


# Table of file names to strings to match and replace.
# The special $$VERSION$$ string will be replaced with the version number
# during both matching and replacing.
# All paths are relative to the root of the repository.
MATCHERS = {
    'change_version.py':
        ["CURRENT_VERSION = '$$VERSION$$'"],
    'pom.xml':
        ["<version>$$VERSION$$</version>"],
    'README.md':
        ["rust-maven-plugin:$$VERSION$$:build",
         "<version>$$VERSION$$</version>",
         "-$$VERSION$$.jar"],
    'jar-jni/pom.xml':
        ["<version>$$VERSION$$</version>"],
    'rust-maven-plugin/pom.xml':
        ["<version>$$VERSION$$</version>"],
    'rust-maven-example/pom.xml':
        ["<version>$$VERSION$$</version>"],
}


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('version', help='Version number to change to')
    parser.add_argument('-p', '--preview', action='store_true',
                        help='Preview changes without making them')
    parser.add_argument('-n', '--num-lines', type=int, default=3,
                        help='Number of lines of context to show in diff')
    parser.add_argument('--no-color', action='store_true',
                        help='Disable color-coded diff output')
    return parser.parse_args()


def main():
    args = parse_args()
    old_contents = {}
    new_contents = {}
    for filename, matchers in MATCHERS.items():
        file_path = pathlib.Path(filename)
        with open(file_path, 'r', encoding='utf-8') as f:
            contents = f.read()
        old_contents[file_path] = contents

        for matcher in matchers:
            old_version_text = matcher.replace('$$VERSION$$', CURRENT_VERSION)
            new_version_text = matcher.replace('$$VERSION$$', args.version)
            if old_version_text not in contents:
                raise RuntimeError(
                    'Could not find "{}" in file "{}"'.format(
                        old_version_text, file_path))
            contents = contents.replace(old_version_text, new_version_text)

        new_contents[file_path] = contents

    for file_path, new_text in new_contents.items():
        if args.preview:
            old_text = old_contents[file_path]
            filename = str(file_path)
            colors = not args.no_color
            if colors:
                green = '\x1b[38;5;16;48;5;2m'
                red = '\x1b[38;5;16;48;5;1m'
                end = '\x1b[0m'
            else:
                green = ''
                red = ''
                end = ''
            diff = difflib.unified_diff(
                old_text.splitlines(keepends=True),
                new_text.splitlines(keepends=True),
                fromfile=filename,
                tofile=filename,
                lineterm='',
                n=args.num_lines)
            if colors:
                for line in diff:
                    if line.startswith('+') and not line.startswith('+++'):
                        print(green + line + end, end='')
                    elif line.startswith('-') and not line.startswith('---'):
                        print(red + line + end, end='')
                    else:
                        print(line, end='')
            else:
                for line in diff:
                    print(line, end='')
        else:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_text)


if __name__ == '__main__':
    main()

