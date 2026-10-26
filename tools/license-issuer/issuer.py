#!/usr/bin/env python3
"""
Emisor de licencias IPV (Sprint 5, spec §4.8) — HERRAMIENTA INTERNA.

La clave privada Ed25519 NUNCA sale de esta máquina ni se empaqueta en la app.
La app solo embebe la pública (LicenseVerifier.PUBLIC_KEY_B64).

Uso:
  python3 issuer.py --genkey                      # genera ipv_private.key (guardar en bóveda)
  python3 issuer.py issue \
      --business "Cafetería La Cueva" \
      --tier PREMIUM \
      --device-id <device_id del negocio, pantalla Respaldo/Licencia> \
      [--months 12] [--modules invoices,cashier] -o licencia.ipvlicense

Formato .ipvlicense: payload JSON canónico + "\n.sig:" + base64(firma Ed25519(payload)).
Requiere: pip install pynacl
"""
import argparse, base64, json, sys, time
from pathlib import Path

try:
    from nacl.signing import SigningKey, VerifyKey
    from nacl.encoding import RawEncoder
except ImportError:
    sys.exit("Falta PyNaCl: pip install pynacl")

KEYFILE = Path(__file__).parent / "ipv_private.key"


def genkey():
    if KEYFILE.exists():
        sys.exit(f"Ya existe {KEYFILE}. Bórrala manualmente si quieres rotar la clave.")
    sk = SigningKey.generate()
    KEYFILE.write_bytes(bytes(sk))
    KEYFILE.chmod(0o600)
    pub = base64.b64encode(bytes(sk.verify_key)).decode()
    print("Clave privada guardada en ipv_private.key (conservarla offline).")
    print("Clave PÚBLICA para embeber en LicenseVerifier.PUBLIC_KEY_B64:")
    print(f'  const val PUBLIC_KEY_B64 = "{pub}"')


def issue(args):
    if not KEYFILE.exists():
        sys.exit("No hay clave privada. Ejecuta primero: issuer.py --genkey")
    sk = SigningKey(KEYFILE.read_bytes())
    expires = int((time.time() + args.months * 30.44 * 86400) * 1000) if args.months else None
    payload = {
        "business": args.business,
        "tier": args.tier,
        "modules": sorted(args.modules.split(",")) if args.modules else [],
        "deviceId": args.device_id,
        "expiresAt": expires,
        "issuedAt": int(time.time() * 1000),
        "schema": 1,
    }
    payload_str = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    sig = sk.sign(payload_str.encode(), encoder=RawEncoder).signature
    out = payload_str + "\n.sig:" + base64.b64encode(sig).decode()
    dest = Path(args.output or f"{args.business.replace(' ', '_')}.ipvlicense")
    dest.write_text(out)
    # autoverificación con la pública derivada (mínima garantía antes de enviar al cliente)
    VerifyKey(bytes(sk.verify_key)).verify(payload_str.encode(), sig)
    print(f"Licencia firmada y auto-verificada → {dest}")
    print(f"  negocio={args.business} tier={args.tier} dispositivo={args.device_id} "
          f"expira={'perpetua' if expires is None else time.strftime('%Y-%m-%d', time.localtime(expires/1000))}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--genkey", action="store_true")
    sub = ap.add_subparsers(dest="cmd")
    it = sub.add_parser("issue")
    it.add_argument("--business", required=True)
    it.add_argument("--tier", choices=["FREE", "PREMIUM"], default="PREMIUM")
    it.add_argument("--device-id", required=True)
    it.add_argument("--months", type=int, default=None, help="Sin este flag = perpetua")
    it.add_argument("--modules", default="", help="lista separada por comas (ej: invoices,cashier)")
    it.add_argument("-o", "--output")
    args = ap.parse_args()
    if args.genkey:
        genkey()
    elif args.cmd == "issue":
        issue(args)
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
