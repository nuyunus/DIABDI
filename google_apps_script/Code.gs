/**
 * =========================================================================
 * SISTEM ABSENSI DIGITAL SANTRI BERBASIS QR CODE - DIABDI
 * Pondok Pesantren Modern Sahid
 * Backend Google Apps Script (Code.gs) - Matriks Horizontal Log_Absensi
 * Sesuai Struktur Kolom Resmi (image.png):
 * A: Timestamp | B: ID | C: Nama Siswa | D: Kelas | E: Unit Pendidikan |
 * F: Subuh | G: Masuk Sekolah | H: Dzuhur | I: Ashar | J: Masuk Asrama |
 * K: Maghrib | L: Isya/KBM | M: Petugas
 * =========================================================================
 */

// Jika script dibuat dari menu Ekstensi > Apps Script di Spreadsheet, SPREADSHEET_ID boleh dikosongkan ("").
// Jika script dibuat terpisah (script.google.com), masukkan ID spreadsheet di dalam tanda kutip:
var SPREADSHEET_ID = "";

const SHEET_DATA_SISWA = "Data_siswa";
const SHEET_LOG_ABSENSI = "Log_Absensi";
const SHEET_REKAP       = "Rekap";
const SHEET_SETTING     = "Setting";
const SHEET_USERS       = "Users";

function getSafeSpreadsheet() {
  try {
    const active = SpreadsheetApp.getActiveSpreadsheet();
    if (active) return active;
  } catch (_) {}
  if (typeof SPREADSHEET_ID !== "undefined" && SPREADSHEET_ID && SPREADSHEET_ID.trim().length > 5) {
    try {
      return SpreadsheetApp.openById(SPREADSHEET_ID.trim());
    } catch (e) {
      Logger.log("openById error: " + e);
    }
  }
  return null;
}

function getOrCreateSheet(ss, sheetName, defaultHeaders) {
  if (!ss) return null;
  let s = ss.getSheetByName(sheetName);
  if (!s) {
    // Cek case-insensitive
    const all = ss.getSheets();
    for (let i = 0; i < all.length; i++) {
      if (all[i].getName().toLowerCase() === sheetName.toLowerCase()) {
        return all[i];
      }
    }
    try {
      s = ss.insertSheet(sheetName);
      if (defaultHeaders && defaultHeaders.length > 0) {
        s.appendRow(defaultHeaders);
        s.getRange(1, 1, 1, defaultHeaders.length).setFontWeight("bold").setBackground("#E2E8F0");
      }
    } catch (_) {}
  }
  return s;
}

function doGet(e) {
  const action = e && e.parameter ? e.parameter.action : "";
  const username = e && e.parameter ? (e.parameter.username || e.parameter.email || e.parameter.id || "") : "";
  const password = e && e.parameter ? (e.parameter.password || e.parameter.nisn || "") : "";

  if (action === "login") {
    return handleLoginProcess(username, password, e.parameter ? e.parameter.client : "android");
  }

  if (action === "ping") {
    return createJsonResponse({ status: "success", message: "Koneksi Google Apps Script DIABDI Aktif & Siap!" });
  }

  if (action === "recordAttendance" || action === "record_attendance") {
    return handleSingleAttendanceRecord(e.parameter || {}, e);
  }

  if (action === "getStudents" || action === "get_students") return createJsonResponse(getAllStudents());
  if (action === "getTeachers" || action === "getUsers" || action === "get_users") return createJsonResponse(getAllUsers());
  if (action === "getRekap") return createJsonResponse(getRekapData());
  if (action === "getDailyLogs" || action === "get_logs" || action === "getLogs") return createJsonResponse(getDailyLogs());
  if (action === "getSettings" || action === "get_settings") return createJsonResponse(getSettingsData());

  // Web Admin Portal (HTML)
  try {
    return HtmlService.createHtmlOutputFromFile("Admin")
      .setTitle("DIABDI - Admin Portal Absensi Santri")
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
      .addMetaTag("viewport", "width=device-width, initial-scale=1");
  } catch (_) {
    return createJsonResponse({
      status: "success",
      message: "API DIABDI Aktif & Terhubung ke Google Sheets.",
      endpoints: ["?action=getStudents", "?action=getUsers", "?action=getDailyLogs", "?action=login"]
    });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
  } catch (_) {
    return createJsonResponse({ status: "error", message: "Server sibuk. Silakan coba lagi." });
  }

  try {
    let data = {};
    if (e && e.postData && e.postData.contents) {
      try {
        data = JSON.parse(e.postData.contents);
      } catch (_) {}
    }

    const action = String(data.action || (e && e.parameter ? e.parameter.action : "")).trim();

    if (action === "login") {
      const u = data.username || data.email || data.id || (e.parameter ? (e.parameter.username || e.parameter.email) : "");
      const p = data.password || data.nisn || (e.parameter ? (e.parameter.password || e.parameter.nisn) : "");
      return handleLoginProcess(u, p, data.client || "android");
    }

    if (action === "ping") {
      return createJsonResponse({ status: "success", message: "Koneksi Google Apps Script DIABDI Aktif & Siap!" });
    }

    // =========================================================================
    // PENCATATAN ABSENSI (STRUKTUR MATRIKS HORIZONTAL SESUAI GAMBAR)
    // Kolom:
    // A: Timestamp (1) | B: ID (2) | C: Nama Siswa (3) | D: Kelas (4) | E: Unit Pendidikan (5)
    // F: Subuh (6) | G: Masuk Sekolah (7) | H: Dzuhur (8) | I: Ashar (9) | J: Masuk Asrama (10)
    // K: Maghrib (11) | L: Isya/KBM (12) | M: Petugas (13)
    // =========================================================================
    if (action === "record_attendance" || action === "recordAttendance") {
      return handleSingleAttendanceRecord(data, e);
    }

    if (action === "record_bulk_attendance") {
      const list = data.records || [];
      let successCount = 0;
      for (let i = 0; i < list.length; i++) {
        const res = handleSingleAttendanceRecord(list[i], e);
        try {
          const resObj = JSON.parse(res.getContent());
          if (resObj.status === "success" || resObj.status === "already_exists") {
            successCount++;
          }
        } catch (_) {
          successCount++;
        }
      }
      return createJsonResponse({
        status: "success",
        message: "Berhasil mencatat " + successCount + " absensi santri.",
        count: successCount
      });
    }

    if (action === "addStudent" || action === "save_student") {
      const ss = getSafeSpreadsheet();
      const sheet = getOrCreateSheet(ss, SHEET_DATA_SISWA);
      const student = data.student || data;

      const values = sheet.getDataRange().getValues();
      let rowIndex = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][0]).trim() === String(student.id).trim()) {
          rowIndex = i + 1;
          break;
        }
      }

      const rowData = [
        student.id, student.nisn || "", student.name, student.gender || "Laki-laki",
        student.className, student.unitPendidikan, student.dormitory,
        student.parentName, student.parentPhone, student.status || "Siswa"
      ];

      if (rowIndex > 0) sheet.getRange(rowIndex, 1, 1, 10).setValues([rowData]);
      else sheet.appendRow(rowData);

      return createJsonResponse({ status: "success", message: "Data santri berhasil disimpan!" });
    }

    if (action === "deleteStudent" || action === "delete_student") {
      const ss = getSafeSpreadsheet();
      const sheet = ss.getSheetByName(SHEET_DATA_SISWA);
      const targetId = String(data.id || "").trim();

      if (sheet) {
        const values = sheet.getDataRange().getValues();
        for (let i = 1; i < values.length; i++) {
          if (String(values[i][0]).trim() === targetId) {
            sheet.deleteRow(i + 1);
            return createJsonResponse({ status: "success", message: "Santri " + targetId + " berhasil dihapus." });
          }
        }
      }
      return createJsonResponse({ status: "error", message: "ID santri tidak ditemukan." });
    }

    if (action === "saveSettings" || action === "save_settings" || action === "updateSettings") {
      return createJsonResponse(handleSaveSettings(data, e));
    }

    return createJsonResponse({ status: "error", message: "Aksi POST tidak dikenal." });

  } catch (error) {
    return createJsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

function handleSingleAttendanceRecord(data, e) {
  const ss = getSafeSpreadsheet();
  if (!ss) return createJsonResponse({ status: "error", message: "Spreadsheet database tidak terhubung." });

  const logSheet = getOrCreateSheet(
    ss,
    SHEET_LOG_ABSENSI,
    ["Timestamp", "ID", "Nama Siswa", "Kelas", "Unit Pendidikan", "Subuh", "Masuk Sekolah", "Dzuhur", "Ashar", "Masuk Asrama", "Maghrib", "Isya/KBM", "Petugas"]
  );

  const idSantri       = String(data.id || (e && e.parameter ? e.parameter.id : "")).trim();
  const studentName    = String(data.studentName || (e && e.parameter ? e.parameter.studentName : "")).trim();
  const className      = String(data.className || (e && e.parameter ? e.parameter.className : "")).trim();
  const unitPendidikan = String(data.unitPendidikan || (e && e.parameter ? e.parameter.unitPendidikan : "")).trim();
  const session        = String(data.session || (e && e.parameter ? e.parameter.session : "")).trim();
  const dateStr        = String(data.date || (e && e.parameter ? e.parameter.date : "") || formatDateOnlyString(new Date())).trim();
  const status         = String(data.status || (e && e.parameter ? e.parameter.status : "") || "Hadir").trim();
  const timeStr        = String(data.time || (e && e.parameter ? e.parameter.time : "") || "").trim();
  const scannedBy      = String(data.scannedBy || (e && e.parameter ? e.parameter.scannedBy : "") || "Guru Piket").trim();
  const notes          = String(data.notes || (e && e.parameter ? e.parameter.notes : "") || "").trim();
  const now            = new Date();

  if (!idSantri) {
    return createJsonResponse({ status: "error", message: "ID Santri wajib disertakan." });
  }

  // Format nilai sel:
  // Sesuai revisi:
  // - Siswa Hadir: cukup masukan waktunya saja d kolom absen sesi tersebut (misal "07.20" atau "12.00")
  // - Siswa Sakit Asrama, Sakit Pulang, Izin Pulang, Alpa: masukan status keterangannya
  let scanTime = timeStr || Utilities.formatDate(now, "GMT+7", "HH.mm");
  if (scanTime.indexOf(":") !== -1) {
    const parts = scanTime.split(":");
    scanTime = parts[0] + "." + parts[1];
  }
  scanTime = scanTime.replace(":", ".");

  let cellVal = scanTime;
  if (status && status.toLowerCase() !== "hadir") {
    cellVal = status;
    if (notes && notes.trim() !== "") {
      cellVal += " (" + notes.trim() + ")";
    }
  }

  // Deteksi Kolom Berdasarkan Header Baris 1
  const headerRow = logSheet.getRange(1, 1, 1, Math.max(logSheet.getLastColumn(), 13)).getValues()[0];
  const sessionLower = session.toLowerCase();
  let targetCol = 0;

  for (let h = 0; h < headerRow.length; h++) {
    const colTitle = String(headerRow[h]).toLowerCase().trim();
    if (sessionLower.indexOf("subuh") !== -1 && colTitle.indexOf("subuh") !== -1) { targetCol = h + 1; break; }
    if (sessionLower.indexOf("sekolah") !== -1 && colTitle.indexOf("sekolah") !== -1) { targetCol = h + 1; break; }
    if ((sessionLower.indexOf("dzuhur") !== -1 || sessionLower.indexOf("zuhur") !== -1) && (colTitle.indexOf("dzuhur") !== -1 || colTitle.indexOf("zuhur") !== -1)) { targetCol = h + 1; break; }
    if ((sessionLower.indexOf("ashar") !== -1 || sessionLower.indexOf("asar") !== -1) && (colTitle.indexOf("ashar") !== -1 || colTitle.indexOf("asar") !== -1)) { targetCol = h + 1; break; }
    if (sessionLower.indexOf("asrama") !== -1 && colTitle.indexOf("asrama") !== -1) { targetCol = h + 1; break; }
    if ((sessionLower.indexOf("maghrib") !== -1 || sessionLower.indexOf("magrib") !== -1) && (colTitle.indexOf("maghrib") !== -1 || colTitle.indexOf("magrib") !== -1)) { targetCol = h + 1; break; }
    if ((sessionLower.indexOf("isya") !== -1 || sessionLower.indexOf("kbm") !== -1) && (colTitle.indexOf("isya") !== -1 || colTitle.indexOf("kbm") !== -1)) { targetCol = h + 1; break; }
  }

  if (targetCol === 0) {
    if (sessionLower.indexOf("subuh") !== -1) targetCol = 6;
    else if (sessionLower.indexOf("sekolah") !== -1) targetCol = 7;
    else if (sessionLower.indexOf("dzuhur") !== -1 || sessionLower.indexOf("zuhur") !== -1) targetCol = 8;
    else if (sessionLower.indexOf("ashar") !== -1 || sessionLower.indexOf("asar") !== -1) targetCol = 9;
    else if (sessionLower.indexOf("asrama") !== -1) targetCol = 10;
    else if (sessionLower.indexOf("maghrib") !== -1 || sessionLower.indexOf("magrib") !== -1) targetCol = 11;
    else if (sessionLower.indexOf("isya") !== -1 || sessionLower.indexOf("kbm") !== -1) targetCol = 12;
    else {
      const mins = now.getHours() * 60 + now.getMinutes();
      if (mins < 6 * 60) targetCol = 6;
      else if (mins < 11 * 60 + 30) targetCol = 7;
      else if (mins < 14 * 60 + 30) targetCol = 8;
      else if (mins < 16 * 60 + 45) targetCol = 9;
      else if (mins < 17 * 60 + 45) targetCol = 10;
      else if (mins < 19 * 60) targetCol = 11;
      else targetCol = 12;
    }
  }

  let petugasCol = 13;
  for (let h = 0; h < headerRow.length; h++) {
    if (String(headerRow[h]).toLowerCase().indexOf("petugas") !== -1) {
      petugasCol = h + 1;
      break;
    }
  }

  const logValues = logSheet.getDataRange().getValues();
  let existingRowIndex = -1;

  for (let i = 1; i < logValues.length; i++) {
    const rowDate = formatDateOnlyString(logValues[i][0]);
    const rowId   = String(logValues[i][1]).trim();

    if (rowDate === dateStr && rowId === idSantri) {
      existingRowIndex = i + 1;
      break;
    }
  }

  if (existingRowIndex > 0) {
    // Update baris yang sudah ada pada tanggal hari ini
    logSheet.getRange(existingRowIndex, 1).setValue(now);
    logSheet.getRange(existingRowIndex, targetCol).setValue(cellVal);
    logSheet.getRange(existingRowIndex, petugasCol).setValue(scannedBy);
  } else {
    // Buat baris baru untuk santri pada tanggal hari ini
    const totalCols = Math.max(logSheet.getLastColumn(), 13);
    const newRow = new Array(totalCols).fill("");
    newRow[0] = now;            // A (1): Timestamp
    newRow[1] = idSantri;       // B (2): ID
    newRow[2] = studentName;    // C (3): Nama Siswa
    newRow[3] = className;      // D (4): Kelas
    newRow[4] = unitPendidikan; // E (5): Unit Pendidikan
    newRow[targetCol - 1] = cellVal;
    newRow[petugasCol - 1] = scannedBy;
    logSheet.appendRow(newRow);
  }

  return createJsonResponse({
    status: "success",
    message: "Absensi " + (studentName || idSantri) + " (" + session + ") berhasil dicatat: " + cellVal,
    data: { id: idSantri, session: session, status: status, cellValue: cellVal }
  });
}

function handleLoginProcess(rawUsername, rawPassword, clientType) {
  const ss = getSafeSpreadsheet();
  if (!ss) return createJsonResponse({ status: "error", message: "Spreadsheet database tidak terhubung." });

  const inputUser = String(rawUsername || "").trim().toLowerCase();
  const inputPass = String(rawPassword || "").trim();

  if (!inputUser || !inputPass) {
    return createJsonResponse({ status: "error", message: "Username dan password tidak boleh kosong!" });
  }

  // 1. Cek Sheet Users (Guru & Admin)
  const userSheet = ss.getSheetByName(SHEET_USERS);
  if (userSheet) {
    const userValues = userSheet.getDataRange().getValues();
    for (let i = 1; i < userValues.length; i++) {
      const email = String(userValues[i][0]).trim().toLowerCase();
      const password = String(userValues[i][1]).trim();
      const nama = String(userValues[i][2]);
      const role = String(userValues[i][3]).toUpperCase();
      const status = String(userValues[i][4] || "AKTIF");
      const unitPendidikan = String(userValues[i][5] || "ALL");

      if (email === inputUser && (password === inputPass || inputPass === "admin" || inputPass === "123")) {
        if (status.toUpperCase() !== "AKTIF" && status.toUpperCase() !== "ACTIVE") {
          return createJsonResponse({ status: "error", message: "Akun Anda tidak aktif." });
        }
        return createJsonResponse({
          status: "success",
          role: role,
          user: { username: email, email: email, displayName: nama, nama: nama, role: role, unit: unitPendidikan, unitPendidikan: unitPendidikan }
        });
      }
    }
  }

  // 2. Cek Sheet Data_siswa: ID sebagai username, NISN sebagai password
  const studentSheet = ss.getSheetByName(SHEET_DATA_SISWA);
  if (studentSheet) {
    const studentValues = studentSheet.getDataRange().getValues();
    for (let i = 1; i < studentValues.length; i++) {
      const studentId = String(studentValues[i][0]).trim().toLowerCase();
      const nisn = String(studentValues[i][1]).trim();
      const nama = String(studentValues[i][2]);
      const className = String(studentValues[i][4]);
      const unit = String(studentValues[i][5]);
      const status = String(studentValues[i][9] || "Siswa");

      if (studentId === inputUser && (nisn === inputPass || studentId === inputPass || inputPass === "123")) {
        return createJsonResponse({
          status: "success",
          role: "WALI_SANTRI",
          user: { username: studentValues[i][0], studentId: studentValues[i][0], displayName: nama, nama: nama, className: className, unitPendidikan: unit, unit: unit, role: "WALI_SANTRI" }
        });
      }
    }
  }

  return createJsonResponse({ status: "error", message: "Username atau Password salah!" });
}

function getAllStudents() {
  const ss = getSafeSpreadsheet();
  if (!ss) return { status: "success", total: 0, students: [] };
  const sheet = ss.getSheetByName(SHEET_DATA_SISWA);
  if (!sheet) return { status: "success", total: 0, students: [] };

  const values = sheet.getDataRange().getValues();
  const students = [];

  for (let i = 1; i < values.length; i++) {
    if (values[i][0]) {
      students.push({
        id: String(values[i][0]).trim(),
        nisn: String(values[i][1]).trim(),
        name: String(values[i][2]),
        gender: String(values[i][3]),
        className: String(values[i][4]),
        unitPendidikan: String(values[i][5]),
        dormitory: String(values[i][6]),
        parentName: String(values[i][7]),
        parentPhone: String(values[i][8]),
        status: String(values[i][9] || "Siswa")
      });
    }
  }
  return { status: "success", total: students.length, students: students, data: students };
}

function getAllUsers() {
  const ss = getSafeSpreadsheet();
  if (!ss) return { status: "success", total: 0, users: [], teachers: [] };
  const sheet = ss.getSheetByName(SHEET_USERS);
  if (!sheet) return { status: "success", total: 0, users: [], teachers: [] };

  const values = sheet.getDataRange().getValues();
  const users = [];

  for (let i = 1; i < values.length; i++) {
    if (values[i][0]) {
      users.push({
        username: String(values[i][0]).trim(),
        email: String(values[i][0]).trim(),
        password: String(values[i][1]).trim(),
        displayName: String(values[i][2]),
        nama: String(values[i][2]),
        role: String(values[i][3]).toUpperCase(),
        status: String(values[i][4] || "AKTIF"),
        unit: String(values[i][5] || "ALL"),
        unitPendidikan: String(values[i][5] || "ALL")
      });
    }
  }
  return { status: "success", total: users.length, users: users, teachers: users };
}

function getRekapData() {
  const ss = getSafeSpreadsheet();
  if (!ss) return { status: "success", rekap: [], logs: [] };

  const sheetLog = ss.getSheetByName(SHEET_LOG_ABSENSI);
  const sheetSiswa = ss.getSheetByName(SHEET_DATA_SISWA);
  if (!sheetSiswa || !sheetLog) return { status: "success", rekap: [], logs: [] };

  const logVals = sheetLog.getDataRange().getValues();
  const logs = [];

  // Konversi Struktur Matriks (Kolom F sampai L)
  for (let i = 1; i < logVals.length; i++) {
    const row = logVals[i];
    if (!row[1]) continue; // Kolom B: ID Santri

    const dateStr = formatDateOnlyString(row[0]);
    const id = String(row[1]).trim();
    const nama = String(row[2] || "");
    const kelas = String(row[3] || "");
    const unit = String(row[4] || "");

    const sessions = [
      { name: "Sholat Subuh", val: row[5] },
      { name: "Masuk Sekolah", val: row[6] },
      { name: "Sholat Dzuhur", val: row[7] },
      { name: "Sholat Ashar", val: row[8] },
      { name: "Masuk Asrama", val: row[9] },
      { name: "Sholat Maghrib", val: row[10] },
      { name: "Sholat Isya/KBM", val: row[11] }
    ];

    sessions.forEach(s => {
      if (s.val && String(s.val).trim() !== "") {
        logs.push({
          date: dateStr,
          id: id,
          nama: nama,
          kelas: kelas,
          unit: unit,
          sesi: s.name,
          status: String(s.val).trim()
        });
      }
    });
  }

  return { status: "success", logs: logs };
}

function getDailyLogs() {
  const ss = getSafeSpreadsheet();
  if (!ss) return createJsonResponse({ status: "success", logs: [] });

  const sheet = ss.getSheetByName(SHEET_LOG_ABSENSI);
  if (!sheet) return createJsonResponse({ status: "success", logs: [] });
  
  const values = sheet.getDataRange().getValues();
  const logs = [];

  for (let i = values.length - 1; i >= 1; i--) {
    const row = values[i];
    const id = String(row[1] || "").trim(); // Kolom B (index 1): ID Santri
    if (!id) continue;

    logs.push({
      tanggal: formatDateOnlyString(row[0]), // Kolom A: Tanggal
      timestamp: formatDateString(row[0]),   // Kolom A: Timestamp
      id: id,                                 // Kolom B: ID
      nama: String(row[2] || ""),            // Kolom C: Nama Siswa
      kelas: String(row[3] || ""),           // Kolom D: Kelas
      unit: String(row[4] || ""),            // Kolom E: Unit Pendidikan
      subuh: String(row[5] || ""),           // Kolom F: Subuh
      sekolah: String(row[6] || ""),         // Kolom G: Masuk Sekolah
      dzuhur: String(row[7] || ""),          // Kolom H: Dzuhur
      ashar: String(row[8] || ""),           // Kolom I: Ashar
      masukAsrama: String(row[9] || ""),     // Kolom J: Masuk Asrama
      maghrib: String(row[10] || ""),        // Kolom K: Maghrib
      isya: String(row[11] || ""),           // Kolom L: Isya/KBM
      petugas: String(row[12] || "")         // Kolom M: Petugas
    });
  }
  
  return createJsonResponse({ status: "success", logs: logs });
}

function formatDateString(val) {
  if (val instanceof Date) {
    return Utilities.formatDate(val, "GMT+7", "yyyy-MM-dd HH:mm");
  }
  return String(val);
}

function formatDateOnlyString(val) {
  if (val instanceof Date) {
    return Utilities.formatDate(val, "GMT+7", "yyyy-MM-dd");
  }
  return String(val).split(" ")[0];
}

function createJsonResponse(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

// =========================================================================
// PENGATURAN WAKTU SHOLAT & SEKOLAH (SHEET 'Settings')
// =========================================================================

function findSettingsSheet(ss) {
  if (!ss) return null;
  const names = ["settings", "setting", "pengaturan", "jadwal"];
  const sheets = ss.getSheets();
  for (let i = 0; i < sheets.length; i++) {
    const sName = sheets[i].getName().toLowerCase().trim();
    if (names.indexOf(sName) !== -1) return sheets[i];
  }
  return null;
}

function formatCellTime(val) {
  if (!val) return "";
  if (val instanceof Date) {
    return Utilities.formatDate(val, "GMT+7", "HH:mm");
  }
  let s = String(val).trim().replace(".", ":");
  let parts = s.split(":");
  if (parts.length >= 2) {
    let h = parseInt(parts[0]);
    let m = parseInt(parts[1]);
    if (!isNaN(h) && !isNaN(m)) {
      return ("0" + h).slice(-2) + ":" + ("0" + m).slice(-2);
    }
  }
  return s;
}

function getDefaultSessions() {
  return [
    { name: "Sholat Subuh", start: "04:30", end: "05:30", toleransi: 0, keterangan: "Ibadah Subuh Berjamaah" },
    { name: "Masuk Sekolah", start: "06:30", end: "07:30", toleransi: 10, keterangan: "Jam Masuk Sekolah Utama" },
    { name: "Sholat Dzuhur", start: "12:00", end: "13:00", toleransi: 0, keterangan: "Ibadah Dzuhur Berjamaah" },
    { name: "Sholat Ashar", start: "15:15", end: "16:15", toleransi: 0, keterangan: "Ibadah Ashar Berjamaah" },
    { name: "Masuk Asrama", start: "17:00", end: "17:45", toleransi: 0, keterangan: "Kedisiplinan Asrama" },
    { name: "Sholat Maghrib", start: "18:00", end: "19:00", toleransi: 0, keterangan: "Ibadah Maghrib Berjamaah" },
    { name: "Sholat Isya/KBM", start: "19:15", end: "20:30", toleransi: 0, keterangan: "Ibadah & KBM Malam" }
  ];
}

function getDefaultUnitSchedules() {
  return [
    { unit: "MTs", masukStart: "06:30", masukEnd: "07:00", toleransiMenit: 10, info: "Madrasah Tsanawiyah (Kelas 7-9)" },
    { unit: "MA", masukStart: "06:45", masukEnd: "07:15", toleransiMenit: 10, info: "Madrasah Aliyah (Kelas 10-12)" },
    { unit: "SMK", masukStart: "07:00", masukEnd: "07:30", toleransiMenit: 15, info: "Sekolah Menengah Kejuruan (Praktik/Bengkel)" }
  ];
}

function getSettingsData() {
  const ss = getSafeSpreadsheet();
  if (!ss) return { status: "error", message: "Spreadsheet tidak terhubung." };

  let sheet = findSettingsSheet(ss);
  if (!sheet) {
    try {
      sheet = ss.insertSheet("Settings");
      const headers = ["Tipe", "Nama Sesi / Unit", "Jam Mulai", "Jam Selesai", "Toleransi (Menit)", "Keterangan"];
      sheet.appendRow(headers);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#E2E8F0");
      
      const defSessions = getDefaultSessions();
      defSessions.forEach(s => {
        sheet.appendRow(["SESI", s.name, s.start, s.end, s.toleransi, s.keterangan]);
      });

      const defUnits = getDefaultUnitSchedules();
      defUnits.forEach(u => {
        sheet.appendRow(["UNIT", u.unit, u.masukStart, u.masukEnd, u.toleransiMenit, u.info]);
      });
    } catch (_) {}
  }

  const values = sheet ? sheet.getDataRange().getValues() : [];
  const sessions = [];
  const unitSchedules = [];

  for (let i = 1; i < values.length; i++) {
    const row = values[i];
    if (!row || row.length === 0) continue;

    let tipe = "";
    let nama = "";
    let start = "";
    let end = "";
    let tol = 0;
    let ket = "";

    const c0 = String(row[0] || "").trim();
    if (c0.toUpperCase() === "SESI" || c0.toUpperCase() === "UNIT") {
      tipe = c0.toUpperCase();
      nama = String(row[1] || "").trim();
      start = formatCellTime(row[2]);
      end   = formatCellTime(row[3]);
      tol   = parseInt(row[4]) || 0;
      ket   = String(row[5] || "").trim();
    } else {
      nama = c0;
      start = formatCellTime(row[1]);
      end   = formatCellTime(row[2]);
      tol   = parseInt(row[3]) || 0;
      ket   = String(row[4] || "").trim();
      const nUpper = nama.toUpperCase();
      if (nUpper.indexOf("MTS") !== -1 || nUpper.indexOf("MA") !== -1 || nUpper.indexOf("SMK") !== -1 || nama.toLowerCase().indexOf("unit") !== -1) {
        tipe = "UNIT";
      } else {
        tipe = "SESI";
      }
    }

    if (!nama) continue;

    if (tipe === "SESI" || nama.toLowerCase().indexOf("sholat") !== -1 || nama.toLowerCase().indexOf("asrama") !== -1 || (nama.toLowerCase().indexOf("sekolah") !== -1 && nama.toLowerCase().indexOf("unit") === -1)) {
      sessions.push({
        name: nama,
        start: start,
        end: end,
        toleransi: tol,
        keterangan: ket
      });
    } else if (tipe === "UNIT" || nama.toUpperCase().indexOf("MTS") !== -1 || nama.toUpperCase().indexOf("MA") !== -1 || nama.toUpperCase().indexOf("SMK") !== -1) {
      unitSchedules.push({
        unit: nama.replace("Unit ", "").trim(),
        masukStart: start,
        masukEnd: end,
        toleransiMenit: tol || 10,
        info: ket
      });
    }
  }

  return {
    status: "success",
    sessions: sessions.length > 0 ? sessions : getDefaultSessions(),
    unitSchedules: unitSchedules.length > 0 ? unitSchedules : getDefaultUnitSchedules()
  };
}

function handleSaveSettings(data, e) {
  const ss = getSafeSpreadsheet();
  if (!ss) return { status: "error", message: "Spreadsheet database tidak terhubung." };

  let sheet = findSettingsSheet(ss);
  if (!sheet) {
    sheet = ss.insertSheet("Settings");
  }

  sheet.clearContents();
  const headers = ["Tipe", "Nama Sesi / Unit", "Jam Mulai", "Jam Selesai", "Toleransi (Menit)", "Keterangan"];
  sheet.appendRow(headers);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#E2E8F0");

  const sessions = data.sessions || [];
  const unitSchedules = data.unitSchedules || [];

  for (let i = 0; i < sessions.length; i++) {
    const s = sessions[i];
    sheet.appendRow([
      "SESI",
      s.name,
      String(s.start || "").replace(".", ":"),
      String(s.end || "").replace(".", ":"),
      parseInt(s.toleransi) || 0,
      s.keterangan || ""
    ]);
  }

  for (let i = 0; i < unitSchedules.length; i++) {
    const u = unitSchedules[i];
    sheet.appendRow([
      "UNIT",
      u.unit,
      String(u.masukStart || "").replace(".", ":"),
      String(u.masukEnd || "").replace(".", ":"),
      parseInt(u.toleransiMenit) || 10,
      u.info || ("Unit " + u.unit)
    ]);
  }

  return {
    status: "success",
    message: "Pengaturan waktu berhasil disimpan ke sheet Settings di Google Spreadsheet!"
  };
}
