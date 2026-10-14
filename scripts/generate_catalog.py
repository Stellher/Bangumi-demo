#!/usr/bin/env python3
"""Build bundled, searchable API catalog from official Bangumi OpenAPI specs."""
from __future__ import annotations
import json
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
SPECS = [
    ("bangumi-v0.yaml", "Bangumi 新版公开 API"),
    ("bangumi-legacy.yml", "Bangumi 旧版兼容 API"),
]

def deref(spec, value, visited=None):
    if not isinstance(value, dict):
        return value
    visited = set() if visited is None else set(visited)
    ref = value.get("$ref")
    if ref and isinstance(ref, str) and ref.startswith("#/") and ref not in visited:
        part = spec
        for k in ref[2:].split("/"):
            part = part[k.replace("~1", "/").replace("~0", "~")]
        expanded = deref(spec, part, visited | {ref})
        if isinstance(expanded, dict):
            return {**expanded, **{k:v for k,v in value.items() if k!="$ref"}}
    return value

def sample(spec, schema, depth=0, visited=None):
    visited = set() if visited is None else set(visited)
    if not isinstance(schema, dict) or depth > 5:
        return None
    ref = schema.get("$ref")
    if ref:
        if ref in visited:
            return None
        schema = deref(spec, schema, visited)
        visited.add(ref)
    if "example" in schema:
        return schema["example"]
    if "default" in schema:
        return schema["default"]
    if "const" in schema:
        return schema["const"]
    if "enum" in schema and schema["enum"]:
        return schema["enum"][0]
    if "allOf" in schema:
        for item in schema["allOf"]:
            v = sample(spec, item, depth+1, visited)
            if isinstance(v, dict):
                return v
    if "oneOf" in schema or "anyOf" in schema:
        return sample(spec, (schema.get("oneOf") or schema.get("anyOf"))[0], depth+1, visited)
    kind = schema.get("type")
    if kind == "array":
        v = sample(spec, schema.get("items", {}), depth+1, visited)
        return [v] if v is not None else []
    if kind == "object" or "properties" in schema:
        props = schema.get("properties", {})
        required = set(schema.get("required", []))
        result = {}
        for name, sub in props.items():
            if name not in required:
                continue
            if name in ("keyword", "name", "title"):
                result[name] = "日常" if name == "keyword" else "示例"
                continue
            result[name] = sample(spec, sub, depth+1, visited)
        return result
    if kind == "integer" or kind == "number":
        return 1
    if kind == "boolean":
        return False
    if kind == "string":
        return "示例"
    return None

def short_type(spec, schema):
    schema = deref(spec, schema) if isinstance(schema, dict) else {}
    if not isinstance(schema, dict):
        return "string"
    return schema.get("type") or ("object" if "properties" in schema else "string")

def get_auth(operation, global_security):
    security = operation.get("security", global_security)
    if security is None:
        return "none", []
    if security == []:
        return "none", []
    auth = "none"
    scopes = []
    for part in security:
        for key, values in part.items():
            if "optional" in str(key).lower():
                if auth != "required":
                    auth = "optional"
            elif "bearer" in str(key).lower() or "oauth" in str(key).lower():
                auth = "required"
            scopes.extend(values or [])
    return auth, sorted(set(scopes))

def build_endpoint(spec, method, path, operation, source):
    parameters = []
    for raw in operation.get("parameters", []):
        p = deref(spec, raw)
        if not isinstance(p, dict):
            continue
        schema = deref(spec, p.get("schema", {}))
        value = p.get("example", schema.get("example", schema.get("default", "")))
        enum = schema.get("enum", [])
        name = p.get("name", "")
        if name == "type" and value == "" and "image" in path:
            value = "small"
        parameters.append({
            "name": name, "location": p.get("in", "query"),
            "required": bool(p.get("required", False)),
            "description": str(p.get("description") or schema.get("description") or "").strip(),
            "type": short_type(spec, schema),
            "example": str(value) if value is not None else "",
            "enum": [str(x) for x in enum],
        })
    raw_body = operation.get("requestBody")
    body = None
    if raw_body:
        b = deref(spec, raw_body)
        content = b.get("content", {})
        media = "application/json" if "application/json" in content else next(iter(content), "")
        s = content.get(media, {}).get("schema", {})
        v = sample(spec, s)
        body = {
            "required": bool(b.get("required", False)),
            "mediaType": media,
            "description": str(b.get("description") or ""),
            "example": json.dumps(v, ensure_ascii=False, indent=2) if v is not None else "",
            "schema": json.dumps(s, ensure_ascii=False, indent=2),
        }
    responses = []
    for status, val in (operation.get("responses") or {}).items():
        v = deref(spec, val)
        responses.append({
            "status": str(status),
            "description": str(v.get("description") or ""),
            "contentTypes": list((v.get("content") or {}).keys()),
        })
    auth, scopes = get_auth(operation, spec.get("security", []))
    return {
        "id": f"{method.upper()} {path}",
        "method": method.upper(),
        "path": path,
        "tag": (operation.get("tags") or ["其他"])[0],
        "summary": str(operation.get("summary") or operation.get("operationId") or path),
        "description": str(operation.get("description") or "").strip(),
        "operationId": str(operation.get("operationId") or ""),
        "auth": auth, "scopes": scopes,
        "params": parameters,
        "requestBody": body,
        "responses": responses,
        "deprecated": bool(operation.get("deprecated", False)),
        "source": source,
    }

def main():
    out = []
    for filename, source in SPECS:
        spec = yaml.safe_load((ROOT / "specs" / filename).read_text(encoding="utf-8"))
        for path, group in (spec.get("paths") or {}).items():
            for method, data in group.items():
                if method.lower() in ("get", "post", "put", "patch", "delete", "options", "head") and isinstance(data, dict):
                    out.append(build_endpoint(spec, method, path, data, source))
    out.sort(key=lambda e: (e["tag"], e["path"], e["method"]))
    result = {"source": "bangumi/api official OpenAPI (bundled snapshot)", "count": len(out), "endpoints": out}
    directory = ROOT / "app" / "src" / "main" / "assets"
    directory.mkdir(parents=True, exist_ok=True)
    output = directory / "bangumi_api_catalog.json"
    output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("Generated", len(out), "documented API operations; legacy:",
          sum(1 for a in out if "旧版" in a["source"]), "->", output)
    assert len(out) >= 56, "API catalog unexpectedly incomplete"

if __name__ == "__main__":
    main()
