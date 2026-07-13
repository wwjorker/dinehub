#!/usr/bin/env python3
"""Generate load users and HS256 tokens for the seckill plan."""

import argparse
import base64
import hashlib
import hmac
import json
import time
from pathlib import Path


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def token(user_id: int, secret: str, expires_at: int) -> str:
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = b64url(json.dumps({"userId": user_id, "exp": expires_at}, separators=(",", ":")).encode())
    body = f"{header}.{payload}"
    signature = b64url(hmac.new(secret.encode(), body.encode(), hashlib.sha256).digest())
    return f"{body}.{signature}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=20_000)
    parser.add_argument("--start-id", type=int, default=100_000)
    parser.add_argument("--secret", required=True)
    parser.add_argument("--tokens", type=Path, default=Path("user_tokens.csv"))
    parser.add_argument("--sql", type=Path, default=Path("users_seed.sql"))
    args = parser.parse_args()

    expires_at = int(time.time()) + 24 * 60 * 60
    user_ids = range(args.start_id, args.start_id + args.count)
    args.tokens.write_text("\n".join(token(user_id, args.secret, expires_at) for user_id in user_ids) + "\n",
                           encoding="utf-8")

    statements = []
    batch = []
    for user_id in user_ids:
        batch.append(f"({user_id},'load-{user_id}','load{user_id}',now())")
        if len(batch) == 500:
            statements.append("insert into user(id,openid,name,create_time) values\n" +
                              ",\n".join(batch) + "\non duplicate key update name=values(name);")
            batch = []
    if batch:
        statements.append("insert into user(id,openid,name,create_time) values\n" +
                          ",\n".join(batch) + "\non duplicate key update name=values(name);")
    args.sql.write_text("\n".join(statements) + "\n", encoding="utf-8")
    print(f"generated {args.count} users: {args.tokens} and {args.sql}")


if __name__ == "__main__":
    main()
