#!/usr/bin/python3

import argparse
import json
import logging
import os
import sys
import tornado.ioloop

import server
import util

logger = logging.getLogger("mdl-ref-server.main")

if __name__ == "__main__":
    sys.stdout.reconfigure(line_buffering=True)

    logging.basicConfig(format='[%(name)s] %(levelname)s: %(message)s', level=logging.DEBUG)
    logger.info("Starting up")

    parser = argparse.ArgumentParser()
    parser.add_argument("--port",
                        help="Port number for HTTP server",
                        default="18013")
    parser.add_argument("--database",
                        help="Path to database file",
                        default="mdl-server-db.sqlite3")
    parser.add_argument("--reset-with-testdata",
                        help="Reset database with test data",
                        action="store_true")
    args = parser.parse_args(sys.argv[1:])
    database_path = vars(args).get("database")
    if database_path is None:
        print("Option --database not given")
        sys.exit(1)

    if vars(args).get("reset_with_testdata"):
        try:
            os.remove(database_path)
        except FileNotFoundError:
            pass

    port_str = vars(args).get("port")
    if port_str is None:
        print("Option --port not given")
        sys.exit(1)
    port = int(port_str)

    server = server.Server(database_path)

    if vars(args).get("reset_with_testdata"):
        util.setup_test_data(server.database)

    app = server.get_app()
    app.listen(port)

    loop = tornado.ioloop.IOLoop.current()
    loop.start()
