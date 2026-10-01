"""Fail CI if the candidate certificate is absent from Android Google OAuth."""
import argparse
import json
from pathlib import Path
import re


def verify(config, certificate_report=None):
    project = config["project_info"]
    assert project["project_id"] == "viaggi-camper", "Wrong Firebase project"
    package = "it.feriepermessi.nativeapp"
    clients = [c for c in config["client"] if
               c["client_info"]["android_client_info"]["package_name"] == package]
    assert len(clients) == 1, "Expected exactly one matching Android app"
    client = clients[0]
    assert client["client_info"]["mobilesdk_app_id"].startswith(
        f'1:{project["project_number"]}:android:'), "Invalid Android app ID"
    oauth = client.get("oauth_client", [])
    assert any(c["client_type"] == 3 and c.get("client_id") for c in oauth), "Missing web OAuth client"
    fingerprints = {c["android_info"]["certificate_hash"].replace(":", "").lower()
                    for c in oauth if c["client_type"] == 1 and
                    c.get("android_info", {}).get("package_name") == package}
    assert fingerprints and all(re.fullmatch(r"[0-9a-f]{40}", f) for f in fingerprints), "Missing/invalid Android SHA-1"
    if certificate_report is not None:
        certificates = re.findall(r"certificate SHA-1 digest:\s*([0-9a-fA-F:]+)", certificate_report)
        assert certificates, "No APK signer certificate found"
        assert all(c.replace(":", "").lower() in fingerprints for c in certificates), "APK signing certificate is not registered in Firebase; register its SHA-1 and supply updated JSON"
    return package


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--certificate-report", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    report = args.certificate_report.read_text() if args.certificate_report else None
    print("Firebase configuration verified:", verify(json.loads((root / "app/google-services.json").read_text()), report))
