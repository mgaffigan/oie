#!/usr/bin/env python3
import sys

from main import main


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(str(error), file=sys.stderr, flush=True)
        sys.exit(1)
