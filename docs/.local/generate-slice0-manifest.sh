#!/usr/bin/env bash
set -euo pipefail

# 生成 Slice 0 的临时 manifest；结果只用于本地审计，不作为已冻结派单清单。
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$root/docs/.local"
source_revision="$(git -C "$root/meta-eova/eova" rev-parse HEAD)"
manifest_revision="provisional-$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$out"
java_manifest="$out/java-manifest.jsonl"
front_manifest="$out/frontend-manifest.jsonl"
summary="$out/manifest-summary.json"
: > "$java_manifest"
: > "$front_manifest"
java_index=1
front_index=1

while IFS= read -r source; do
  rel="${source#meta-eova/eova/core/src/main/java/}"
  base="${rel##*/}"
  class_name="${base%.java}"
  package_name="$(sed -nE 's/^package[[:space:]]+([^;]+);/\1/p' "$root/$source" | head -1)"
  source_fqcn="${package_name}.${class_name}"
  source_sha="$(shasum -a 256 "$root/$source" | awk '{print $1}')"
  line_count="$(wc -l < "$root/$source" | tr -d ' ')"
  if rg -q 'extends[[:space:]]+JFinalConfig|com\.jfinal\.render|com\.jfinal\.config|com\.jfinal\.aop|com\.jfinal\.route|implements[[:alnum:]_]*Plugin' "$root/$source"; then
    classification="D"
  elif rg -q 'com\.jfinal\.plugin\.activerecord\.(Db|Record)|com\.jfinal\.plugin\.activerecord\.Model' "$root/$source" || printf '%s' "$rel" | rg -q '(^|/)(service|widget|model|sql)/'; then
    classification="C"
  elif rg -q 'com\.jfinal\.kit\.(Kv|JsonKit|LogKit)|com\.jfinal\.template|cn\.eova\.tools\.(x|EovaTool)' "$root/$source"; then
    classification="B"
  else
    classification="A"
  fi
  target="remis-eova/backend/yudao-cloud/yudao-module-eova/eova-core/src/main/java/$rel"
  printf '{"manifestRevision":"%s","unitId":"R2-JAVA-%04d","unitType":"java","sourcePath":"%s","sourceFqcn":"%s","targetPaths":["%s"],"targetMappingStatus":"provisional","parentSourcePath":null,"classification":"%s","sourceRevision":"%s","sourceSha256":"%s","lineCount":%s,"directDependencies":[],"contractRefs":[],"migrationStatus":"unmapped"}\n' "$manifest_revision" "$java_index" "$rel" "$source_fqcn" "$target" "$classification" "$source_revision" "$source_sha" "$line_count" >> "$java_manifest"
  java_index=$((java_index + 1))
done < <(find "$root/meta-eova/eova/core/src/main/java" -type f -name '*.java' | sed "s#^$root/##" | sort)

while IFS= read -r source; do
  rel="${source#meta-eova/eova/}"
  source_sha="$(shasum -a 256 "$root/$source" | awk '{print $1}')"
  line_count="$(wc -l < "$root/$source" | tr -d ' ')"
  case "$rel" in
    view/src/main/resources/webapp/eova/lib/*|*.min.js) classification="vendor"; status="excluded";;
    view/src/main/resources/webapp/eova/error/*) classification="error"; status="deferred";;
    view/src/main/resources/webapp/eova/ui/*) classification="frontend-core"; status="unmapped";;
    view/src/main/resources/webapp/eova/_view/*) classification="frontend-template"; status="unmapped";;
    demo/src/main/webapp/*) classification="demo"; status="unmapped";;
    *) classification="shell"; status="unmapped";;
  esac
  case "$rel" in
    view/src/main/resources/webapp/eova/*) target="remis-eova/fornt/eova-ui/src/${rel#view/src/main/resources/webapp/eova/}";;
    demo/src/main/webapp/*) target="remis-eova/fornt/eova-ui/src/demo/${rel#demo/src/main/webapp/}";;
    *) target="remis-eova/fornt/eova-ui/src/$rel";;
  esac
  printf '{"manifestRevision":"%s","unitId":"R2-FE-%04d","unitType":"frontend","sourcePath":"%s","sourceFqcn":null,"targetPaths":["%s"],"targetMappingStatus":"provisional","parentSourcePath":null,"classification":"%s","sourceRevision":"%s","sourceSha256":"%s","lineCount":%s,"directDependencies":[],"contractRefs":[],"migrationStatus":"%s"}\n' "$manifest_revision" "$front_index" "$rel" "$target" "$classification" "$source_revision" "$source_sha" "$line_count" "$status" >> "$front_manifest"
  front_index=$((front_index + 1))
done < <(find "$root/meta-eova" -type f \( -name '*.js' -o -name '*.vue' -o -name '*.html' \) | sed "s#^$root/##" | sort)

jq -n \
  --arg manifestRevision "$manifest_revision" \
  --arg sourceRevision "$source_revision" \
  --arg javaSha256 "$(shasum -a 256 "$java_manifest" | awk '{print $1}')" \
  --arg frontendSha256 "$(shasum -a 256 "$front_manifest" | awk '{print $1}')" \
  --argjson javaCount "$(wc -l < "$java_manifest" | tr -d ' ')" \
  --argjson frontendCount "$(wc -l < "$front_manifest" | tr -d ' ')" \
  '{manifestRevision:$manifestRevision,sourceRevision:$sourceRevision,status:"provisional",java:{count:$javaCount,sha256:$javaSha256},frontend:{count:$frontendCount,sha256:$frontendSha256},freezeReady:false,freezeBlockers:["target mapping is provisional","directDependencies not audited","contractRefs not audited","workspace persistence probe not executed"]}' > "$summary"

printf 'generated %s\n' "$java_manifest"
printf 'generated %s\n' "$front_manifest"
printf 'generated %s\n' "$summary"
