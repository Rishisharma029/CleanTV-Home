#!/usr/bin/env python3
"""
CleanTV Desktop Manager - MSIX Packager
Builds a Microsoft Store-compliant .msix package.
Prefers makeappx.exe from Windows Kits if available; otherwise uses high-fidelity OPC/ZIP builder.
"""

import os
import sys
import glob
import shutil
import hashlib
import base64
import zipfile
import subprocess
import xml.etree.ElementTree as ET

BLOCK_SIZE = 65536  # 64 KB block size for AppxBlockMap

def find_makeappx():
    # Check PATH first
    path = shutil.which("makeappx.exe") or shutil.which("makeappx") or shutil.which("MakeAppx.exe")
    if path:
        return path

    # Check Windows Kits and WinGet MSIX-Toolkit
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    patterns = [
        os.path.join(local_app_data, r"Microsoft\WinGet\Packages\Microsoft.MSIX-Toolkit_*\MSIX-Toolkit.x64\MakeAppx.exe"),
        os.path.join(local_app_data, r"Microsoft\WinGet\Links\makeappx.exe"),
        r"C:\Program Files (x86)\Windows Kits\10\bin\*\x64\makeappx.exe",
        r"C:\Program Files\Windows Kits\10\bin\*\x64\makeappx.exe",
        r"C:\Program Files (x86)\Windows Kits\8.1\bin\x64\makeappx.exe"
    ]
    for p in patterns:
        matches = glob.glob(p)
        if matches:
            matches.sort(reverse=True)
            return matches[0]
    return None

def build_blockmap(staging_dir, file_rel_paths):
    root = ET.Element("BlockMap", {
        "HashMethod": "http://www.w3.org/2001/04/xmlenc#sha256",
        "xmlns": "http://schemas.microsoft.com/appx/2010/blockmap"
    })

    for rel_path in file_rel_paths:
        full_path = os.path.join(staging_dir, rel_path)
        file_size = os.path.getsize(full_path)
        # Standard ZIP local file header size = 30 + len(filename)
        lfh_size = 30 + len(rel_path.replace("/", "\\"))

        file_elem = ET.SubElement(root, "File", {
            "Name": rel_path.replace("/", "\\"),
            "Size": str(file_size),
            "LfhSize": str(lfh_size)
        })

        with open(full_path, "rb") as f:
            while True:
                chunk = f.read(BLOCK_SIZE)
                if not chunk:
                    if file_size == 0:
                        empty_hash = base64.b64encode(hashlib.sha256(b"").digest()).decode("ascii")
                        ET.SubElement(file_elem, "Block", {"Hash": empty_hash, "Size": "0"})
                    break
                block_hash = base64.b64encode(hashlib.sha256(chunk).digest()).decode("ascii")
                ET.SubElement(file_elem, "Block", {
                    "Hash": block_hash,
                    "Size": str(len(chunk))
                })

    return ET.tostring(root, encoding="utf-8", xml_declaration=True)

def generate_content_types():
    content = """<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="xml" ContentType="application/vnd.ms-appx.manifest+xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="exe" ContentType="application/x-msdownload"/>
  <Default Extension="apk" ContentType="application/vnd.android.package-archive"/>
  <Default Extension="html" ContentType="text/html"/>
  <Default Extension="css" ContentType="text/css"/>
  <Default Extension="js" ContentType="application/javascript"/>
  <Default Extension="json" ContentType="application/json"/>
  <Default Extension="dll" ContentType="application/x-msdownload"/>
  <Default Extension="txt" ContentType="text/plain"/>
  <Default Extension="conf" ContentType="text/plain"/>
  <Default Extension="properties" ContentType="text/plain"/>
  <Override PartName="/AppxBlockMap.xml" ContentType="application/vnd.ms-appx.blockmap+xml"/>
  <Override PartName="/AppxManifest.xml" ContentType="application/vnd.ms-appx.manifest+xml"/>
</Types>
"""
    return content.strip().encode("utf-8")

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    windows_dir = os.path.dirname(script_dir)
    dist_dir = os.path.join(windows_dir, "dist")
    staging_dir = os.path.join(windows_dir, "build_staging")
    out_msix = os.path.join(dist_dir, "CleanTV-Desktop-Manager-v4.0.0.msix")

    os.makedirs(dist_dir, exist_ok=True)
    if os.path.exists(staging_dir):
        shutil.rmtree(staging_dir)
    os.makedirs(staging_dir, exist_ok=True)

    print("Stage 1: Preparing MSIX Payload...")
    # Verify CleanTV.exe exists
    exe_path = os.path.join(windows_dir, "CleanTV.exe")
    if not os.path.exists(exe_path):
        print("CleanTV.exe not found! Compiling first...")
        subprocess.check_call(["powershell", "-ExecutionPolicy", "Bypass", "-File", os.path.join(script_dir, "build-exe.ps1")])

    # Copy files into staging
    shutil.copy2(os.path.join(windows_dir, "AppxManifest.xml"), staging_dir)
    shutil.copy2(exe_path, staging_dir)
    shutil.copytree(os.path.join(windows_dir, "assets"), os.path.join(staging_dir, "assets"))
    shutil.copytree(os.path.join(windows_dir, "src", "ui"), os.path.join(staging_dir, "ui"))

    # Bundle the launcher APK inside the MSIX (so install works without network)
    repo_root = os.path.dirname(windows_dir)
    apk_src = os.path.join(repo_root, "apks", "clean-tv-launcher-latest.apk")
    if os.path.exists(apk_src):
        os.makedirs(os.path.join(windows_dir, "apks"), exist_ok=True)
        shutil.copy2(apk_src, os.path.join(windows_dir, "apks", "clean-tv-launcher-latest.apk"))
        apks_staging = os.path.join(staging_dir, "apks")
        os.makedirs(apks_staging, exist_ok=True)
        shutil.copy2(apk_src, os.path.join(apks_staging, "clean-tv-launcher-latest.apk"))
        print(f" -> Bundled launcher APK ({os.path.getsize(apk_src):,} bytes)")
    else:
        print(" -> WARNING: apks/clean-tv-launcher-latest.apk not found. APK install will require it beside CleanTV.exe.")

    # Bundle platform-tools (adb.exe) if available
    pt_src = os.path.join(windows_dir, "platform-tools")
    if os.path.exists(pt_src) and os.path.exists(os.path.join(pt_src, "adb.exe")):
        shutil.copytree(pt_src, os.path.join(staging_dir, "platform-tools"))
        print(" -> Bundled platform-tools (adb.exe) - no PATH dependency")
    else:
        print(" -> NOTE: platform-tools not bundled. Run scripts/download-platform-tools.ps1 first.")

    # Bundle debloat presets
    presets_src = os.path.join(repo_root, "presets")
    if os.path.exists(presets_src):
        shutil.copytree(presets_src, os.path.join(staging_dir, "presets"))
        print(f" -> Bundled debloat presets ({len(os.listdir(presets_src))} presets)")

    makeappx = find_makeappx()
    if makeappx:
        print(f"Stage 2: Found makeappx at '{makeappx}' - building MSIX package...")
        cmd = [makeappx, "pack", "/d", staging_dir, "/p", out_msix, "/o", "/nv"]
        subprocess.check_call(cmd)
    else:
        print("Stage 2: makeappx not detected; building Store-compliant OPC/ZIP MSIX package...")
        # Gather all payload files
        payload_rel_files = []
        for root, _, files in os.walk(staging_dir):
            for file in files:
                full = os.path.join(root, file)
                rel = os.path.relpath(full, staging_dir)
                payload_rel_files.append(rel)

        # Generate BlockMap
        print(" -> Generating AppxBlockMap.xml (SHA-256 blocks)...")
        blockmap_xml = build_blockmap(staging_dir, payload_rel_files)
        with open(os.path.join(staging_dir, "AppxBlockMap.xml"), "wb") as f:
            f.write(blockmap_xml)

        # Generate [Content_Types].xml
        print(" -> Generating [Content_Types].xml...")
        content_types_xml = generate_content_types()
        with open(os.path.join(staging_dir, "[Content_Types].xml"), "wb") as f:
            f.write(content_types_xml)

        # Package into .msix
        print(f" -> Packaging into {out_msix}...")
        if os.path.exists(out_msix):
            os.remove(out_msix)

        with zipfile.ZipFile(out_msix, "w", compression=zipfile.ZIP_DEFLATED) as z:
            for root, _, files in os.walk(staging_dir):
                for file in files:
                    full = os.path.join(root, file)
                    rel = os.path.relpath(full, staging_dir).replace("\\", "/")
                    z.write(full, arcname=rel)

    size = os.path.getsize(out_msix)
    print(f"\n[SUCCESS] Generated MSIX Package:")
    print(f"Path: {out_msix}")
    print(f"Size: {size:,} bytes")

    # Clean staging
    shutil.rmtree(staging_dir, ignore_errors=True)

if __name__ == "__main__":
    main()

