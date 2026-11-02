# Bộ skill Android cho Claude Code — đề xuất cho Finote

Trạng thái: nghiên cứu ngày 2026-11-02. Bộ P1 đã cài vào `.claude/skills/` (xem mục 6).

## 1. Kết luận nhanh

- Không cài nguyên gói. Chỉ chép từng skill cần dùng vào `.claude/skills/<tên>/` của project.
- Skill không làm giảm token mỗi lượt chạy. Theo eval của chrisbanes trên Claude, token mỗi lượt tăng khoảng 2–66% khi skill được nạp. Đổi lại, tỉ lệ làm đúng tăng (ví dụ `gradle-run` từ 16,7% lên 83,3%). Tiết kiệm thật đến từ việc ít phải làm lại và từ việc không nạp skill thừa.
- Claude Code nạp sẵn `description` của mọi skill (tối đa 1.536 ký tự mỗi skill). Phần thân chỉ nạp khi dùng. Vì vậy càng nhiều skill thì danh sách càng dài và càng dễ kích hoạt nhầm. Skill audit ít dùng nên đặt `disable-model-invocation: true` để ẩn khỏi danh sách và chỉ gọi tay bằng `/tên`.

## 2. Nguồn đã kiểm tra

| Repo | Sao | Push gần nhất | License | Đánh giá |
|---|---:|---|---|---|
| [android/skills](https://github.com/android/skills) (Google) | 10.282 | 2026-10-29 | Apache-2.0 | Chính thức. Chỉ viết cho chỗ LLM hay sai. Có cả XR, TV, Wear, Cars nên không cài hết. |
| [chrisbanes/skills](https://github.com/chrisbanes/skills) | 1.136 | 2026-11-02 | Apache-2.0 | Chất lượng cao nhất. Có eval công khai trên Claude. |
| [new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills) | 998 | 2026-07-27 | Apache-2.0 | Danh sách tổng hợp, không cài trực tiếp. |
| [skydoves/compose-performance-skills](https://github.com/skydoves/compose-performance-skills) | 524 | 2026-06-16 | Apache-2.0 | Hiệu năng Compose. Dùng ở P3, P5. |
| [skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills) | 341 | 2026-05-25 | Apache-2.0 | 54 skill test. Bộ `jvm-tests` có `runTest`, Turbine, Robolectric. |
| [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja) | 130 | 2026-09-29 | Apache-2.0 | Một skill rất lớn. Ép Navigation3, Room 3 và đa module. |
| [haidrrrry/compose-kotlin-agent-skills](https://github.com/haidrrrry/compose-kotlin-agent-skills) | 63 | 2026-07-08 | MIT | MVI nhưng dùng tên `UiEvent`, Navigation3, Ktor. |
| [liuyi0808/android-review](https://github.com/liuyi0808/android-review) | 19 | 2026-08-27 | MIT | Ít người dùng. |
| [krutikJain/android-agent-skills](https://github.com/krutikJain/android-agent-skills) | 14 | 2026-03-25 | MIT | Ít người dùng, lâu không cập nhật. |
| [GuillemRoca/agent-skills-android](https://github.com/GuillemRoca/agent-skills-android) | 5 | 2026-10-03 | MIT | Quá ít người dùng. |
| [aihip/android-claude-code-skills](https://github.com/aihip/android-claude-code-skills) | 7 | 2026-04-07 | Không có | Không có license, loại. |

## 3. Bộ đề xuất theo phase

### Cài ngay (P0 còn lại và P1)

| Skill | Nguồn | Lý do cho Finote |
|---|---|---|
| `gradle-run` | chrisbanes | Bọc Gradle, chỉ trả tóm tắt JSON. Không đổ cả log build vào context. Cần `python3` (máy đã có 3.13). Đã thử trên Windows, xem mục 6. |
| `kotlin-concurrency-and-flow` | chrisbanes | StateFlow, Flow, coroutine cho MVI và repository. Claude: 72,2% → 100%. |
| `compose-state-and-effects` | chrisbanes | State và effect cho màn hình MVI. Claude: 79,2% → 95,8%. |
| `edge-to-edge` | android/skills | Dùng cho `feature/app-shell` (P0, chưa làm). |
| `testing-coroutines-with-runtest`, Turbine, Robolectric | skydoves `jvm-tests/` | DoD P1 cần test rollback `@Transaction` bằng Robolectric. Chép 3 skill này, không chạy script symlink. |

### Cài khi tới phase

| Phase | Skill | Nguồn | Ghi chú |
|---|---|---|---|
| P2 | `android-permissions-security` | android/skills | Kiểm `NotificationListenerService`, `exported`, `TileService`. Khoảng 29 KB, nặng khi nạp. Snippet mẫu có `Log.d` dữ liệu, trái luật privacy của Finote. |
| P2 | `anr-audit-and-fix` | android/skills | Listener chạy trên main thread dễ gây ANR. |
| P3 | `compose-performance` | chrisbanes | Chọn một bộ hiệu năng thôi. Không cài thêm skydoves và Google cùng lúc. |
| P5 | `play-policy-insights` | android/skills | Bắt buộc với app đọc thông báo ngân hàng: data safety, quyền hạn chế. Đặt `disable-model-invocation: true`. |
| P5 | `r8-analyzer`, `android-profiler` | android/skills | Đặt `disable-model-invocation: true`. |
| P5 | `android-benchmark-comparison` | chrisbanes | Đo cold start dưới 1,2 giây. |

### Không dùng

- Plugin `android/skills` cài trọn gói: thêm khoảng 35 skill không liên quan (XR, TV, Wear, Cars, LiteRT).
- `testing-setup` (Google): tự cài JaCoCo, Dropshots, screenshot test và tạo `AGENTS.md`. Trái luật "chỉ thêm dependency khi task cần".
- Drjacky và haidrrrry: Finote đã chuyển sang Navigation3 và Room 3 (D8), nên phần này giờ khớp. Nhưng Drjacky ép đa module, còn haidrrrry dùng tên `UiEvent`, trái luật Konsist (`Contract.kt` phải có `State`, `Intent`, `Effect`). Chỉ đọc tham khảo `references/testing.md` của Drjacky cho Room 3 testing.
- Skill workflow của chrisbanes (`deliver-spec`, `to-plan`, `implement-with-subagents`, `shepherd`): trùng với superpowers và agent `coder`/`reviewer` đã có.

## 4. Phát hiện ảnh hưởng P1

- SQLCipher hỗ trợ Room 3 từ bản 4.18.0 qua driver riêng ([release](https://www.zetetic.net/blog/2026/08/18/sqlcipher-4.18.0-release/), [issue #84](https://github.com/sqlcipher/sqlcipher-android/issues/84)).
- Đã chốt Room 3 (D8 trong roadmap). `feature/db-encryption` dùng driver SQLCipher thay cho `SupportOpenHelperFactory`.
- Thư viện native của SQLCipher không chạy trên JVM thuần. Test Robolectric cần driver khác cho DB mã hóa ([nguồn, bên thứ ba](https://github.com/mcosgriff/glovebox/issues/220)).

## 5. Cách cài (Windows)

- Clone repo nguồn vào thư mục ngoài project, rồi chép từng thư mục skill vào `.claude/skills/`. Không dùng script symlink của skydoves, vì symlink trên Windows cần Developer Mode.
- Ghi commit hoặc ngày của nguồn vào file này khi cài, để biết khi nào cần cập nhật.
- Sau khi cài, mở session mới để Claude Code quét lại skill.

## 6. Đã cài (2026-11-02)

| Skill | Nguồn | Commit nguồn |
|---|---|---|
| `gradle-run` | chrisbanes/skills `skills/gradle-run` | `d83f6e2` |
| `kotlin-concurrency-and-flow` | chrisbanes/skills | `d83f6e2` |
| `compose-state-and-effects` | chrisbanes/skills | `d83f6e2` |
| `edge-to-edge` | android/skills `system/edge-to-edge` | `6daa53e` |
| `testing-coroutines-with-runtest` | skydoves/android-testing-skills `jvm-tests/coroutines` | `8665ed5` |
| `testing-flows-with-turbine` | skydoves/android-testing-skills `jvm-tests/coroutines` | `8665ed5` |
| `using-robolectric-correctly` | skydoves/android-testing-skills `jvm-tests/robolectric` | `8665ed5` |

- Mỗi thư mục có file `LICENSE` của repo nguồn (Apache-2.0).
- `gradle-run`: bỏ `scripts/test_gradle_run.py` và `agents/openai.yaml` (dành cho Codex). Script lưu log trong thư mục temp của hệ thống, có hỗ trợ Windows (`msvcrt`, `taskkill`), không gọi mạng.
- Đã thử `gradle-run` trên Windows: `create`, `run`, `finish` chạy được. Lệnh Gradle phải ghi `.\gradlew.bat`, từ Bash thì để trong nháy đơn `'.\gradlew.bat'`. `./gradlew.bat` lỗi vì cmd không hiểu `./`, còn `gradlew.bat` không có đường dẫn thì báo không tìm thấy file.
- Ví dụ trong skill có version cứng (`testImplementation("...:1.x")`). Luật `CLAUDE.md` thắng: version luôn qua catalog.
- Skill skydoves có link tới skill anh em và `docs/CORPUS.md` không được cài. Link chết không ảnh hưởng nội dung chính.
