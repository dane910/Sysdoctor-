package com.example.diagnostics

import com.example.model.ArchitectureTopic
import com.example.model.FeasibilityLevel

object ArchitectureGuides {
    val topics: List<ArchitectureTopic> = listOf(
        ArchitectureTopic(
            id = "bootable_usb",
            title = "Bootable USB & Recovery Drive",
            subtitle = "USB Gadget Mass Storage vs. OTG Host Imaging",
            feasibility = FeasibilityLevel.REQUIRES_ROOT_KERNEL,
            summary = "Turning a phone into a bootable ISO drive for a PC requires kernel-level USB Gadget ConfigFS access, whereas inspecting or formatting external USB drives over OTG works inside standard Android APIs.",
            sandboxReality = "Stock Android configures the USB-C port via kernel ConfigFS for MTP, PTP, ADB, or RNDIS. Exposing a raw .iso or .img block device as a BIOS-bootable USB Mass Storage (UMS) LUN—similar to DriveDroid—requires a kernel compiled with mass_storage gadget support and root privileges to write /config/usb_gadget/. Unprivileged Play Store apps are blocked by SELinux from modifying gadget descriptors.",
            actionableSteps = listOf(
                "No-Root OTG Approach: Connect a physical USB flash drive via USB-C OTG, inspect it in SysDoctor's USB OTG Inspector, and use Storage Access Framework (SAF) to stage recovery files or verify SHA-256 checksums.",
                "Bootable PC Media without Root: Flash an external microSD or USB stick using a host PC tool (Ventoy, Rufus, or Etcher) or a dedicated USB-C NVMe enclosure.",
                "Custom Kernel / Rooted Research Devices: Check /config/usb_gadget/g1/functions/ on rooted developer devices to verify if mass_storage.0 is compiled into the kernel."
            ),
            speechScript = "Bootable USB and recovery drive feasibility. Making a phone emulate a BIOS-bootable USB drive directly to a PC requires kernel ConfigFS mass storage gadget support and root privileges to bind an ISO image as a logical unit. Standard unprivileged Android apps are blocked by SELinux from reconfiguring USB gadget descriptors. However, without root, you can connect an external flash drive over USB OTG, inspect its hardware descriptor in SysDoctor, and verify recovery payloads via the Storage Access Framework."
        ),
        ArchitectureTopic(
            id = "partition_recovery",
            title = "Partition Recovery & Bootloader",
            subtitle = "A/B Seamless Slots, AVB 2.0, dm-verity & Fastboot",
            feasibility = FeasibilityLevel.ADB_OR_OEM,
            summary = "Android protects system partitions (/system, /vendor, /boot, /vbmeta) with cryptographic dm-verity and Verified Boot, isolating partition recovery to Bootloader/Fastboot and Recovery modes.",
            sandboxReality = "In Android's security model, apps run inside isolated SELinux domains and can only read block statistics on /data and read-only mount tables where permitted. Direct block writes to /dev/block/by-name/* are denied even to privileged system apps once the device is booted, preventing malware from persisting in /system or flashing rogue boot images.",
            actionableSteps = listOf(
                "Official OEM Recovery: Power off and hold the manufacturer key combo (or run 'adb reboot recovery') to access stock recovery or apply signed OTA updates via 'adb sideload update.zip'.",
                "A/B Seamless Slot Recovery: On A/B devices, if an update fails to boot after several attempts, the bootloader automatically falls back to the known-good inactive slot (slot_a or slot_b).",
                "Fastboot Diagnostics: From a trusted workstation with USB Debugging/OEM tools, 'fastboot getvar current-slot' and 'fastboot getvar all' inspect partition health without unlocking the bootloader."
            ),
            speechScript = "Partition recovery and bootloader architecture. Android protects system, vendor, and boot partitions using Android Verified Boot and dm-verity cryptographic hash trees. Running apps cannot write to raw block devices. Partition recovery is handled safely outside the OS via stock Recovery, ADB sideloading of signed OTAs, or automatic A-B slot fallback in the bootloader."
        ),
        ArchitectureTopic(
            id = "network_auditing",
            title = "Mobile Network Security Auditing",
            subtitle = "What's Possible in Sandbox vs. Hardware Limits",
            feasibility = FeasibilityLevel.STANDARD_SANDBOX,
            summary = "Android natively supports defensive Wi-Fi posture auditing, Private DNS (DoT) checks, VPN tunnel verification, and gateway/DNS latency diagnostics without root.",
            sandboxReality = "Unprivileged apps can inspect active network security standards (WPA3-SAE vs WPA2 vs Open/WEP), Private DNS configuration, DNS server IPs, and VPN routing via ConnectivityManager and WifiManager. True offensive pentesting capabilities—such as raw 802.11 monitor mode, frame injection, or ARP spoofing—are blocked by both Android's CAP_NET_RAW restrictions and closed mobile Wi-Fi chip firmware (which normally forbids monitor mode unless using an external USB OTG Wi-Fi adapter with custom kernel drivers).",
            actionableSteps = listOf(
                "Verify Wi-Fi Encryption: Audit every new hotspot in SysDoctor's Audit tab to ensure WPA3-SAE or WPA2-PSK is active and avoid Open or WEP networks.",
                "Enforce Private DNS: Enable DNS-over-TLS in Android Settings > Network & Internet > Private DNS (e.g., dns.quad9.net or one.one.one.one) to prevent ISP/LAN DNS snooping.",
                "Non-Root Local Packet Inspection: Defensive tools like PCAPdroid use Android's official VpnService API to inspect your own device's outbound connections locally without root."
            ),
            speechScript = "Mobile network security auditing. Within the standard Android sandbox, apps can audit active Wi-Fi encryption such as WPA3 versus open networks, verify Private DNS over TLS, detect active VPN tunnels, and probe gateway latency. Hardware-level monitor mode and raw packet injection are blocked by Android permissions and closed Wi-Fi firmware, making local VpnService inspection the best non-root defensive approach."
        ),
        ArchitectureTopic(
            id = "rooting_tradeoffs",
            title = "Rooting Utilities & Integrity Trade-offs",
            subtitle = "Verified Boot, Play Integrity & Non-Root Alternatives",
            feasibility = FeasibilityLevel.REQUIRES_ROOT_KERNEL,
            summary = "Modern Android devices cannot be safely rooted by an in-app exploit utility; unlocking the bootloader wipes user data and impacts hardware-backed security attestation.",
            sandboxReality = "Legacy one-click root apps relied on unpatched kernel privilege-escalation vulnerabilities that are mitigated on modern Android releases via CFI, SELinux enforcing mode, and regular security patches. Legitimate root access on developer devices is achieved strictly via OEM bootloader unlocking and patching boot/init_boot images on a workstation—which triggers Verified Boot Orange/Yellow states and invalidates Play Integrity Strong/Device verdicts for banking and DRM apps.",
            actionableSteps = listOf(
                "Prefer Non-Root Power Tools: Use ADB over USB or Wireless Debugging (Android 11+) with tools like Shizuku to grant controlled shell-level diagnostic APIs to compatible utilities without unlocking the bootloader.",
                "Protect Keystore Integrity: Keep the bootloader locked on your daily-driver phone so hardware-backed encryption keys and biometric credentials remain protected by the Titan M / StrongBox enclave.",
                "Dedicated Test Hardware: Keep security research and custom firmware experimentation isolated to a secondary developer device."
            ),
            speechScript = "Rooting utilities and integrity trade-offs. Modern Android prevents one-click in-app rooting through SELinux and kernel hardening. Legitimate developer root requires unlocking the bootloader on a workstation, which wipes user data and degrades Play Integrity hardware attestation. For daily drivers, Wireless Debugging and ADB-backed power tools offer safe diagnostics without sacrificing hardware security."
        )
    )
}
