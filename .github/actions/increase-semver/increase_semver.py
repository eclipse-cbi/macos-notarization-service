# *******************************************************************************
# Copyright (c) 2023 Eclipse Foundation and others.
# This program and the accompanying materials are made available
# under the terms of the MIT License
# which is available at https://spdx.org/licenses/MIT.html
# SPDX-License-Identifier: MIT
# *******************************************************************************

import sys
from semver.version import Version


def run(current_version: str, version_fragment: str) -> None:
    v = Version.parse(current_version)
    print(str(v.next_version(part=version_fragment)))


if __name__ == "__main__":
    args = sys.argv[1:]

    if len(args) != 2:
        print("Error: Need to provide 2 arguments: 'current-version' and 'version-fragment'.")
        exit(1)

    run(args[0], args[1])
    exit(0)
