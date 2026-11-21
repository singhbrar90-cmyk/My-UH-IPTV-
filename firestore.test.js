const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uh_123";
const BOB_UID = "bob_uh_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favourites").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("preferences").doc("default").set({
      userId: ALICE_UID,
      audioPriority: ["hi", "pa", "en"],
      autoAudioEnabled: true,
      preferHardwareDecoder: true,
      defaultPlayerResizeMode: "FIT",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Preferences: owner create, partial update, and ghost/immutable field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("default");

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      audioPriority: ["hi", "pa", "en"],
      autoAudioEnabled: true,
      preferHardwareDecoder: true,
      defaultPlayerResizeMode: "FIT",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    prefRef.update({
      audioPriority: ["pa", "hi", "en"],
      watchHistoryEnabled: false,
      showFavouriteLiveChannelsFirst: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("other_pref").set({
      id: "other_pref",
      userId: ALICE_UID,
      audioPriority: ["hi", "pa", "en"],
      autoAudioEnabled: true,
      preferHardwareDecoder: true,
      defaultPlayerResizeMode: "FIT",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject createdAt mutation
  await assertFails(
    prefRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    prefRef.update({
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("CategoryVisibility: owner CRUD succeeds and cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const visRef = aliceDb.collection("users").doc(ALICE_UID).collection("categoryVisibility").doc("pl_hash_1");

  await assertSucceeds(
    visRef.set({
      userId: ALICE_UID,
      playlistId: "pl_1",
      liveCategoryIds: ["cat_adult", "cat_sports"],
      movieCategoryIds: ["mov_horror"],
      seriesCategoryIds: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    visRef.update({
      liveCategoryIds: ["cat_adult"],
      movieCategoryIds: [],
      seriesCategoryIds: ["ser_doc"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categoryVisibility").get()
  );

  // Reject immutable playlistId mutation
  await assertFails(
    visRef.update({
      playlistId: "pl_other",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("categoryVisibility").doc("pl_hash_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("categoryVisibility").get()
  );
});

test("Favourites, ContinueWatching, WatchHistory: owner CRUD succeeds and cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const favRef = aliceDb.collection("users").doc(ALICE_UID).collection("favourites").doc("fav_1");
  await assertSucceeds(
    favRef.set({
      userId: ALICE_UID,
      itemId: "ch_101",
      contentType: "LIVE",
      title: "News HD",
      posterUrl: "https://example.com/logo.png",
      categoryName: "News",
      streamUrl: "http://stream.example.com:8080/live/101.ts",
      playlistId: "pl_1",
      createdAt: created,
      updatedAt: created,
    })
  );

  const cwRef = aliceDb.collection("users").doc(ALICE_UID).collection("continueWatching").doc("cw_1");
  await assertSucceeds(
    cwRef.set({
      userId: ALICE_UID,
      itemId: "mov_99",
      contentType: "MOVIE",
      title: "Cinema Classic",
      subtitle: "Action",
      posterUrl: "https://example.com/poster.jpg",
      streamUrl: "http://stream.example.com:8080/movie/99.mp4",
      playlistId: "pl_1",
      seriesId: "",
      seasonNumber: 0,
      episodeNumber: 0,
      positionMs: 120000,
      durationMs: 5400000,
      preferredAudioLang: "hi",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    cwRef.update({
      positionMs: 240000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const histRef = aliceDb.collection("users").doc(ALICE_UID).collection("watchHistory").doc("hist_1");
  await assertSucceeds(
    histRef.set({
      userId: ALICE_UID,
      itemId: "mov_99",
      contentType: "MOVIE",
      title: "Cinema Classic",
      subtitle: "Action",
      posterUrl: "https://example.com/poster.jpg",
      playlistId: "pl_1",
      watchedAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List queries succeed for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("favourites").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("continueWatching").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("watchHistory").get());

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("favourites").doc("fav_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("favourites").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("continueWatching").doc("cw_1").update({ positionMs: 0 }));
});
