# LCosmetics

ระบบเครื่องแต่งกาย (cosmetics) สำหรับเซิร์ฟเวอร์ **Folia** — หมวก เสื้อ เป้ ปีก ลูกโป่ง ของถือมือซ้าย

เขียนขึ้นใหม่ทั้งหมดโดยออกแบบรอบข้อจำกัดของ Folia ตั้งแต่ต้น ไม่ได้ดัดแปลงจาก plugin เดิมตัวใดตัวหนึ่ง

---

## ทำอะไรได้

| ช่อง | ชื่อไทย | วิธีแสดง |
|---|---|---|
| `HELMET` | หมวก | แก้ equipment packet |
| `CHESTPLATE` | เสื้อ | แก้ equipment packet |
| `LEGGINGS` | กางเกง | แก้ equipment packet |
| `BOOTS` | รองเท้า | แก้ equipment packet |
| `OFFHAND` | ของถือมือซ้าย | แก้ equipment packet |
| `BACKPACK` | เป้ / ปีก | ItemDisplay ติดหลัง |
| `BALLOON` | ลูกโป่ง | ItemDisplay ลอยเหนือหัว |

- **เกราะจริงยังป้องกันตามปกติ** — cosmetic เปลี่ยนแค่รูปที่ผู้เล่นคนอื่นเห็น
- **เจ้าตัวเห็นของจริงของตัวเอง** ในช่องเกราะ (ตั้ง `firstperson-item` แยกได้)
- รองรับ item จาก **Nexo** (`material: nexo:ไอดี`) และ item vanilla
- ทุกข้อความในเกมเป็น **ภาษาไทย**
- GUI แบ่งตามช่อง มีปุ่มถอดทั้งหมด
- เซฟข้อมูลผู้เล่นอัตโนมัติ (ไฟล์ YAML ต่อคน ไม่ต้องมี database)

---

## จุดที่ออกแบบให้เบากว่าวิธีที่ plugin ประเภทนี้มักใช้

**1. ไม่มี task เดินตลอดเวลา**

cosmetic ช่องเกราะทำงานโดย **ดักแก้ `ENTITY_EQUIPMENT` packet ที่เซิร์ฟส่งอยู่แล้ว** แทนการตั้ง timer ยิง packet เพิ่มเรื่อยๆ
ผลคือจำนวน packet ที่ส่งออกเท่าเดิมเป๊ะ ไม่มี bandwidth ส่วนเกิน และไม่มี task กิน tick

**2. เป้ / ลูกโป่ง ใช้ passenger ไม่ใช่ timer ย้ายตำแหน่ง**

display entity ถูก `addPassenger` ไว้บนตัวผู้เล่น → client คำนวณตำแหน่งเอง
ไม่มี repeating task ย้าย entity ทุก tick และไม่มี teleport packet ไหลตามผู้เล่น

**3. ทางออกเร็วใน packet listener**

`UserManager` นับจำนวน cosmetic ช่องเกราะที่ใส่อยู่ทั้งเซิร์ฟไว้ใน `AtomicInteger`
ถ้าไม่มีใครใส่อยู่เลย listener จะ `return` ทันทีโดยไม่แตะ packet ที่ไหลผ่าน

**4. เซฟเฉพาะที่เปลี่ยนจริง**

`CosmeticUser` มีธง dirty — autosave เขียนดิสก์แค่คนที่เปลี่ยน cosmetic
ผู้เล่นที่ไม่มี cosmetic เลยจะไม่มีไฟล์ค้างในโฟลเดอร์

**5. display entity ตั้ง `persistent = false`**

ไม่ถูกเขียนลง region file → เซิร์ฟ crash แล้วไม่มี entity ผีค้างในโลก

---

## รองรับ Folia อย่างไร

Folia แยก thread ตาม region ทำให้โค้ดแบบ Bukkit เดิมพังทันทีเวลาแตะ entity ข้าม region

| งาน | Scheduler ที่ใช้ |
|---|---|
| spawn / remove display entity | `player.getScheduler()` (entity scheduler ของผู้เล่นคนนั้น) |
| โหลด / เซฟไฟล์ผู้เล่น | `getServer().getAsyncScheduler()` |
| autosave | `getAsyncScheduler().runAtFixedRate` |
| อ่านสถานะจาก netty thread | `ConcurrentHashMap` + `AtomicInteger` เท่านั้น |

ไม่มีการเรียก `Bukkit.getScheduler()` ที่ไหนเลย

---

## ติดตั้ง

**ต้องมี:** Folia 26.1.x, Java 25, [packetevents](https://github.com/retrooper/packetevents)
**ไม่บังคับ:** Nexo (สำหรับ item โมเดลกำหนดเอง), PlaceholderAPI, LuckPerms

1. วาง `LCosmetics-1.0.0.jar` ใน `plugins/`
2. เริ่มเซิร์ฟ — plugin จะสร้าง `plugins/LCosmetics/config.yml` และ `cosmetics.yml`
3. แก้ `cosmetics.yml` ให้ตรงกับ item ที่มีใน Nexo ของคุณ
4. `/cos reload`

---

## คำสั่ง

| คำสั่ง | ทำอะไร | สิทธิ์ |
|---|---|---|
| `/cos` | เปิดเมนู | ทุกคน |
| `/cos toggle` | ซ่อน / แสดง cosmetic ของตัวเอง | ทุกคน |
| `/cos apply <ไอดี> [ผู้เล่น]` | ใส่ให้ | `lcosmetics.command.apply.other` (กรณีให้คนอื่น) |
| `/cos unapply <ช่อง> [ผู้เล่น]` | ถอดช่องเดียว | — |
| `/cos clear [ผู้เล่น]` | ถอดทั้งหมด | — |
| `/cos menu [ผู้เล่น]` | เปิดเมนูให้คนอื่น | `lcosmetics.command.menu.other` |
| `/cos list` | ดูรายการทั้งหมด | — |
| `/cos reload` | โหลด config ใหม่ | `lcosmetics.command.reload` |

alias: `/lcosmetics`, `/lcos`, `/cos`

---

## ตั้งค่าเครื่องแต่งกาย

```yaml
beanie:
  slot: HELMET
  permission: "lcosmetics.beanie"   # เว้นว่าง = ทุกคนใช้ได้
  item:
    material: nexo:beanie           # หรือชื่อ vanilla เช่น DIAMOND_HELMET
    name: "<rainbow>หมวกไหมพรมสีรุ้ง</rainbow>"
    lore:
      - "&7หมวกกันหนาวสีสันสดใส"

backpack:
  slot: BACKPACK
  permission: "lcosmetics.backpack"
  item:
    material: nexo:backpack
    name: "&9เป้สะพายหลัง"
  firstperson-item:                 # รูปที่เจ้าตัวเห็นเอง
    material: nexo:backpack_thirdperson
```

รองรับทั้ง MiniMessage (`<red>`, `<rainbow>`) และโค้ดสีเก่า (`&c`) ในไฟล์เดียวกัน

---

## Bedrock (Geyser)

- **หมวก / เสื้อ / กางเกง / รองเท้า / มือซ้าย** — ใช้ได้ (เป็น equipment ปกติ)
- **เป้ / ปีก / ลูกโป่ง** — Bedrock render `ItemDisplay` ไม่ได้ ผู้เล่น Bedrock จะไม่เห็นช่องพวกนี้

---

## Build

```bash
gradle build
```

jar ออกที่ `build/libs/LCosmetics-1.0.0.jar`
หรือ download จากแท็บ **Actions** → เลือก run → **Artifacts**

---

## License

MIT
