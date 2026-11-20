const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_teacher_bf";
const BOB_UID = "bob_teacher_bf";
const CHARLIE_UID = "charlie_teacher_bf";
const ADMIN_UID = "admin_onadja";
const ADMIN_EMAIL = "louple815@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(ms = 5000) {
  return new Date(Date.now() - ms);
}

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("posts").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("debates").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("groups").where("privacy", "==", "PUBLIC").get());
  await assertFails(unauth.collection("resources").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("notifications").where("recipientId", "==", ALICE_UID).get());
  await assertFails(unauth.collection("admin_logs").get());
});

test("User registration, PII split isolation, RBAC protection, and Storage URL enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@bf.edu", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@bf.edu", email_verified: true }).firestore();
  const now = pastDate();

  // Reject Base64 photoData in Firestore
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      firstName: "Amina",
      lastName: "Ouédraogo",
      displayName: "Amina Ouédraogo",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      photoData: "data:image/jpeg;base64,/9j/4AAQ",
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject non-HTTPS / data: photoUrl
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      firstName: "Amina",
      lastName: "Ouédraogo",
      displayName: "Amina Ouédraogo",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      photoUrl: "data:image/png;base64,AAAA",
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid teacher profile creation with Cloud Storage photoUrl
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      firstName: "Amina",
      lastName: "Ouédraogo",
      displayName: "Amina Ouédraogo",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      subject: "Mathématiques",
      schoolLevel: "Lycée",
      photoUrl: "https://firebasestorage.googleapis.com/v0/b/profplus/o/avatars%2Falice.jpg?alt=media",
      photoStoragePath: `avatars/${ALICE_UID}/profile.jpg`,
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject self-assignment of ADMINISTRATEUR role or verified=true
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      firstName: "Bob",
      lastName: "Kaboré",
      displayName: "Bob Kaboré",
      role: "ADMINISTRATEUR",
      country: "Burkina Faso",
      city: "Bobo-Dioulasso",
      region: "Hauts-Bassins",
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      firstName: "Bob",
      lastName: "Kaboré",
      displayName: "Bob Kaboré",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Bobo-Dioulasso",
      region: "Hauts-Bassins",
      verified: true,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject profile update escalating role or verified status
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMINISTRATEUR",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid profile update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      city: "Koudougou",
      updatedAt: pastDate(1000),
    })
  );

  // Private PII doc isolated to owner only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@bf.edu",
      notifyComments: true,
      notifyReplies: true,
      notifyMentions: true,
      notifyMessages: true,
      notifyInvitations: true,
      notifyAnnouncements: true,
      notifyGroups: true,
      blockedUserIds: [],
      preferredLanguage: "fr",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
});

test("Email spoofing test: unverified admin email cannot access admin resources", async () => {
  const spoofedAdmin = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const realAdmin = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertFails(spoofedAdmin.collection("admin_logs").get());
  await assertFails(
    spoofedAdmin.collection("admin_logs").doc("log_1").set({
      adminId: "spoof_uid",
      adminName: "Spoof",
      action: "VERIFY",
      targetType: "USER",
      targetId: ALICE_UID,
      createdAt: pastDate(),
    })
  );

  await assertSucceeds(realAdmin.collection("admin_logs").get());
  await assertSucceeds(
    realAdmin.collection("admin_logs").doc("log_1").set({
      adminId: ADMIN_UID,
      adminName: "Admin ONADJA",
      action: "VERIFY_USER",
      targetType: "USER",
      targetId: ALICE_UID,
      details: "Verified teacher credential",
      createdAt: pastDate(),
    })
  );
});

test("Correction 7 & Server-Side Counters: posts likes require atomic reactions, other counters cannot be inflated client-side", async () => {
  const { createRequire } = require("node:module");
  const r = createRequire(require.resolve("@firebase/rules-unit-testing"));
  const firebase = r("firebase/compat/app");
  r("firebase/compat/firestore");
  const FV = firebase.firestore.FieldValue;

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  // Reject post with legacy imageData Base64
  await assertFails(
    aliceDb.collection("posts").doc("post_b64").set({
      authorId: ALICE_UID,
      authorName: "Amina Ouédraogo",
      authorRole: "ENSEIGNANT",
      authorVerified: false,
      content: "Post avec Base64 interdit",
      category: "Méthodes pédagogiques",
      visibility: "PUBLIC",
      imageData: "data:image/jpeg;base64,/9j/",
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Amina Ouédraogo",
      authorRole: "ENSEIGNANT",
      authorVerified: false,
      content: "Nouvelle fiche de mathématiques pour la classe de 3ème au Burkina Faso.",
      category: "Méthodes pédagogiques",
      visibility: "PUBLIC",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/profplus/o/posts%2Fimg.jpg?alt=media",
      imageStoragePath: `posts/${ALICE_UID}/images/img.jpg`,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query succeeds, unfiltered fails
  await assertSucceeds(aliceDb.collection("posts").where("visibility", "==", "PUBLIC").get());
  await assertFails(aliceDb.collection("posts").get());

  // Bob cannot edit Alice's post content or arbitrarily set likesCount
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      content: "Hacked",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: 999,
      updatedAt: pastDate(1000),
    })
  );

  // Standalone likesCount +1 WITHOUT atomic reaction doc MUST FAIL (Correction 7)
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );

  // Direct client increment of commentsCount / sharesCount MUST FAIL (handled server-side by Cloud Functions)
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      commentsCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      sharesCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );

  // Atomic batch creating reaction + likesCount +1 SUCCEEDS (Correction 7)
  const likeBatch = bobDb.batch();
  likeBatch.set(bobDb.collection("posts").doc("post_1").collection("reactions").doc(BOB_UID), {
    postId: "post_1",
    userId: BOB_UID,
    type: "LIKE",
    createdAt: FV.serverTimestamp(),
  });
  likeBatch.update(bobDb.collection("posts").doc("post_1"), {
    likesCount: FV.increment(1),
    updatedAt: FV.serverTimestamp(),
  });
  await assertSucceeds(likeBatch.commit());

  // Standalone likesCount -1 WITHOUT deleting reaction MUST FAIL
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: FV.increment(-1),
      updatedAt: FV.serverTimestamp(),
    })
  );

  // Atomic batch deleting reaction + likesCount -1 SUCCEEDS
  const unlikeBatch = bobDb.batch();
  unlikeBatch.delete(bobDb.collection("posts").doc("post_1").collection("reactions").doc(BOB_UID));
  unlikeBatch.update(bobDb.collection("posts").doc("post_1"), {
    likesCount: FV.increment(-1),
    updatedAt: FV.serverTimestamp(),
  });
  await assertSucceeds(unlikeBatch.commit());

  // Comment & share event creation succeed (triggers update counts server-side)
  await assertSucceeds(
    bobDb.collection("comments").doc("comment_1").set({
      targetId: "post_1",
      targetType: "POST",
      authorId: BOB_UID,
      authorName: "Bob Kaboré",
      authorRole: "ENSEIGNANT",
      content: "Très utile pour nos élèves !",
      visibility: "PUBLIC",
      status: "ACTIVE",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("shares").doc("share_1").set({
      postId: "post_1",
      userId: BOB_UID,
      createdAt: pastDate(1000),
    })
  );
});

test("Debates, polls, and atomic batch voting (argumentsCount protected)", async () => {
  const { createRequire } = require("node:module");
  const r = createRequire(require.resolve("@firebase/rules-unit-testing"));
  const firebase = r("firebase/compat/app");
  r("firebase/compat/firestore");
  const FV = firebase.firestore.FieldValue;

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("debates").doc("deb_1").set({
      authorId: ALICE_UID,
      authorName: "Amina Ouédraogo",
      authorVerified: false,
      title: "Numérique au lycée",
      question: "Comment intégrer les outils numériques hors-ligne en classe ?",
      category: "Technologie éducative",
      visibility: "PUBLIC",
      status: "OPEN",
      argumentsCount: 0,
      pollId: "poll_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client cannot directly inflate argumentsCount
  await assertFails(
    bobDb.collection("debates").doc("deb_1").update({
      argumentsCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("polls").doc("poll_1").set({
      debateId: "deb_1",
      authorId: ALICE_UID,
      question: "Utilisez-vous des supports numériques ?",
      options: ["Oui régulièrement", "Parfois", "Pas encore"],
      optionCounts: [0, 0, 0],
      totalVotes: 0,
      visibility: "PUBLIC",
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-atomic poll increment without vote doc must fail (existsAfter guard)
  await assertFails(
    bobDb.collection("polls").doc("poll_1").update({
      optionCounts: [1, 0, 0],
      totalVotes: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Atomic WriteBatch creating vote + updating poll counts succeeds
  const batch = bobDb.batch();
  batch.set(bobDb.collection("votes").doc(`poll_1_${BOB_UID}`), {
    pollId: "poll_1",
    debateId: "deb_1",
    userId: BOB_UID,
    optionIndex: 0,
    visibility: "PUBLIC",
    createdAt: pastDate(1000),
  });
  batch.update(bobDb.collection("polls").doc("poll_1"), {
    optionCounts: [1, 0, 0],
    totalVotes: 1,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(batch.commit());
});

test("Correction 4: Conversations and messages strict participant match & anti-Base64 attachment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      type: "DIRECT",
      title: "Amina & Bob",
      participantIds: [ALICE_UID, BOB_UID],
      participantSummary: "Amina Ouédraogo, Bob Kaboré",
      lastMessage: "Bonjour collègue",
      lastMessageAt: now,
      createdBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot inject Charlie or omit Alice in message participantIds (Correction 4)
  await assertFails(
    bobDb.collection("messages").doc("msg_bad_participants").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: BOB_UID,
      senderName: "Bob Kaboré",
      text: "Tentative ajout tiers",
      createdAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("messages").doc("msg_omit_alice").set({
      conversationId: "conv_1",
      participantIds: [BOB_UID, CHARLIE_UID],
      senderId: BOB_UID,
      senderName: "Bob Kaboré",
      text: "Omission d'Alice",
      createdAt: pastDate(1000),
    })
  );

  // Reject legacy Base64 attachmentData
  await assertFails(
    bobDb.collection("messages").doc("msg_b64").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob Kaboré",
      text: "Pièce jointe interdite",
      attachmentData: "data:application/pdf;base64,JVBERi0=",
      createdAt: pastDate(1000),
    })
  );

  // Bob cannot add participants to a DIRECT conversation
  await assertFails(
    bobDb.collection("conversations").doc("conv_1").update({
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Valid message with exact matching participantIds + HTTPS Storage attachmentUrl succeeds
  await assertSucceeds(
    bobDb.collection("messages").doc("msg_1").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob Kaboré",
      text: "Bonjour Amina, voici la fiche.",
      attachmentName: "fiche.pdf",
      attachmentUrl: "https://firebasestorage.googleapis.com/v0/b/profplus/o/messages%2Fconv_1%2Ffiche.pdf?alt=media",
      attachmentStoragePath: `messages/conv_1/${BOB_UID}/fiche.pdf`,
      attachmentMimeType: "application/pdf",
      attachmentSizeBytes: 24500,
      createdAt: pastDate(1000),
    })
  );

  // Charlie (non-participant) cannot read conversation or messages or inject a message
  await assertFails(charlieDb.collection("conversations").doc("conv_1").get());
  await assertFails(charlieDb.collection("messages").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(
    charlieDb.collection("messages").doc("msg_intrude").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Intrusion",
      createdAt: pastDate(1000),
    })
  );

  await assertSucceeds(bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get());
  await assertSucceeds(bobDb.collection("messages").where("participantIds", "array-contains", BOB_UID).get());
});

test("Corrections 3 & 8: Group privacy join rules (PUBLIC=ACTIVE, PRIVATE/INVITE_ONLY=PENDING) and membersCount enforcement", async () => {
  const { createRequire } = require("node:module");
  const r = createRequire(require.resolve("@firebase/rules-unit-testing"));
  const firebase = r("firebase/compat/app");
  r("firebase/compat/firestore");
  const FV = firebase.firestore.FieldValue;

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Create PUBLIC & PRIVATE groups
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_pub").set({
      name: "Maths Burkina",
      description: "Groupe public",
      domain: "Matière",
      privacy: "PUBLIC",
      ownerId: ALICE_UID,
      ownerName: "Amina Ouédraogo",
      membersCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_members").doc(`grp_pub_${ALICE_UID}`).set({
      groupId: "grp_pub",
      userId: ALICE_UID,
      userName: "Amina Ouédraogo",
      userRole: "ENSEIGNANT",
      memberRole: "OWNER",
      status: "ACTIVE",
      visibility: "PUBLIC",
      joinedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_priv").set({
      name: "Direction Lycées Centre",
      description: "Groupe privé",
      domain: "Établissement",
      privacy: "PRIVATE",
      ownerId: ALICE_UID,
      ownerName: "Amina Ouédraogo",
      membersCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_members").doc(`grp_priv_${ALICE_UID}`).set({
      groupId: "grp_priv",
      userId: ALICE_UID,
      userName: "Amina Ouédraogo",
      userRole: "RESPONSABLE_ETABLISSEMENT",
      memberRole: "OWNER",
      status: "ACTIVE",
      visibility: "PRIVATE",
      joinedAt: now,
    })
  );

  // Correction 3: Bob cannot bypass PRIVATE validation by setting status=ACTIVE
  await assertFails(
    bobDb.collection("group_members").doc(`grp_priv_${BOB_UID}`).set({
      groupId: "grp_priv",
      userId: BOB_UID,
      userName: "Bob Kaboré",
      userRole: "ENSEIGNANT",
      memberRole: "MEMBER",
      status: "ACTIVE",
      visibility: "PRIVATE",
      joinedAt: pastDate(1000),
    })
  );

  // Bob joining PRIVATE with PENDING succeeds
  await assertSucceeds(
    bobDb.collection("group_members").doc(`grp_priv_${BOB_UID}`).set({
      groupId: "grp_priv",
      userId: BOB_UID,
      userName: "Bob Kaboré",
      userRole: "ENSEIGNANT",
      memberRole: "MEMBER",
      status: "PENDING",
      visibility: "PRIVATE",
      joinedAt: pastDate(1000),
    })
  );

  // Correction 8: Owner Alice cannot increment membersCount without changing a member to ACTIVE
  await assertFails(
    aliceDb.collection("groups").doc("grp_priv").update({
      membersCount: 99,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("groups").doc("grp_priv").update({
      membersCount: FV.increment(1),
      lastMemberUserId: BOB_UID,
      updatedAt: FV.serverTimestamp(),
    })
  );

  // Owner approves Bob atomically (+1 membersCount with lastMemberUserId) -> SUCCEEDS
  const approveBatch = aliceDb.batch();
  approveBatch.update(aliceDb.collection("group_members").doc(`grp_priv_${BOB_UID}`), { status: "ACTIVE" });
  approveBatch.update(aliceDb.collection("groups").doc("grp_priv"), {
    membersCount: FV.increment(1),
    lastMemberUserId: BOB_UID,
    updatedAt: FV.serverTimestamp(),
  });
  await assertSucceeds(approveBatch.commit());

  // Correction 3 & 8: Charlie joins PUBLIC group cleanly with atomic ACTIVE batch
  await assertFails(
    charlieDb.collection("groups").doc("grp_pub").update({
      membersCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );
  await assertFails(
    charlieDb.collection("group_members").doc(`grp_pub_${CHARLIE_UID}`).set({
      groupId: "grp_pub",
      userId: CHARLIE_UID,
      userName: "Charlie",
      userRole: "ENSEIGNANT",
      memberRole: "MEMBER",
      status: "PENDING",
      visibility: "PUBLIC",
      joinedAt: pastDate(500),
    })
  );

  const pubJoinBatch = charlieDb.batch();
  pubJoinBatch.set(charlieDb.collection("group_members").doc(`grp_pub_${CHARLIE_UID}`), {
    groupId: "grp_pub",
    userId: CHARLIE_UID,
    userName: "Charlie",
    userRole: "ENSEIGNANT",
    memberRole: "MEMBER",
    status: "ACTIVE",
    visibility: "PUBLIC",
    joinedAt: pastDate(500),
  });
  pubJoinBatch.update(charlieDb.collection("groups").doc("grp_pub"), {
    membersCount: FV.increment(1),
    updatedAt: FV.serverTimestamp(),
  });
  await assertSucceeds(pubJoinBatch.commit());

  // Correction 8: Charlie leaves PUBLIC group atomically (-1 membersCount + delete ACTIVE doc)
  const pubLeaveBatch = charlieDb.batch();
  pubLeaveBatch.delete(charlieDb.collection("group_members").doc(`grp_pub_${CHARLIE_UID}`));
  pubLeaveBatch.update(charlieDb.collection("groups").doc("grp_pub"), {
    membersCount: FV.increment(-1),
    updatedAt: FV.serverTimestamp(),
  });
  await assertSucceeds(pubLeaveBatch.commit());
});

test("Server-side notifications enforcement, establishments, reports, and verifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  // Client CANNOT create notifications directly (reserved for Cloud Functions Admin SDK)
  await assertFails(
    aliceDb.collection("notifications").doc("notif_spoof").set({
      recipientId: BOB_UID,
      actorId: ALICE_UID,
      actorName: "Amina Ouédraogo",
      type: "COMMENT",
      title: "Fausse notification",
      body: "Tentative client",
      read: false,
      createdAt: now,
    })
  );

  // Seed notification as if written by Cloud Functions Admin SDK
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("notifications").doc("notif_1").set({
      recipientId: BOB_UID,
      actorId: ALICE_UID,
      actorName: "Amina Ouédraogo",
      type: "COMMENT",
      title: "Nouveau commentaire",
      body: "Amina a commenté votre publication.",
      targetId: "post_1",
      read: false,
      createdAt: now,
    });
  });

  await assertFails(aliceDb.collection("notifications").where("recipientId", "==", BOB_UID).get());
  await assertSucceeds(bobDb.collection("notifications").where("recipientId", "==", BOB_UID).get());
  await assertSucceeds(bobDb.collection("notifications").doc("notif_1").update({ read: true }));

  // Establishment (cannot self-verify)
  await assertFails(
    aliceDb.collection("establishments").doc("est_1").set({
      officialName: "Lycée Bogodogo",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      establishmentType: "Lycée",
      directorId: ALICE_UID,
      directorName: "Amina Ouédraogo",
      verified: true,
      visibility: "PUBLIC",
      membersCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("establishments").doc("est_1").set({
      officialName: "Lycée Bogodogo",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      establishmentType: "Lycée",
      directorId: ALICE_UID,
      directorName: "Amina Ouédraogo",
      verified: false,
      visibility: "PUBLIC",
      membersCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Report & verification request & admin approval
  await assertSucceeds(
    bobDb.collection("reports").doc("rep_1").set({
      reporterId: BOB_UID,
      reporterName: "Bob Kaboré",
      targetType: "POST",
      targetId: "post_1",
      reason: "Contenu hors sujet",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("reports").doc("rep_1").update({ status: "REVIEWED", reviewedBy: BOB_UID, updatedAt: pastDate(1000) }));
  await assertSucceeds(adminDb.collection("reports").doc("rep_1").update({ status: "REVIEWED", reviewedBy: ADMIN_UID, updatedAt: pastDate(1000) }));

  await assertSucceeds(
    aliceDb.collection("verification_requests").doc("ver_1").set({
      userId: ALICE_UID,
      userName: "Amina Ouédraogo",
      targetType: "TEACHER",
      targetId: ALICE_UID,
      targetName: "Amina Ouédraogo",
      documentInfo: "Attestation d'affectation MENAPLN n°2026-441",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("verification_requests").doc("ver_1").update({
      status: "APPROVED",
      reviewedBy: ADMIN_UID,
      reviewNote: "Document conforme",
      updatedAt: pastDate(1000),
    })
  );
});

test("Suspended users are blocked from publishing, commenting, debating, or messaging", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      firstName: "Bob",
      lastName: "Kaboré",
      displayName: "Bob Kaboré",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      suspended: true,
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    bobDb.collection("posts").doc("post_susp").set({
      authorId: BOB_UID,
      authorName: "Bob Kaboré",
      authorRole: "ENSEIGNANT",
      authorVerified: false,
      content: "Post interdit",
      category: "Actualités",
      visibility: "PUBLIC",
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      status: "ACTIVE",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("debates").doc("deb_susp").set({
      authorId: BOB_UID,
      authorName: "Bob Kaboré",
      authorVerified: false,
      title: "Débat interdit",
      question: "Question bloquée ?",
      category: "Pédagogie",
      visibility: "PUBLIC",
      status: "OPEN",
      argumentsCount: 0,
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Moderator vs Admin RBAC boundary and Resource Cloud Storage / 20MB / download-event rules", async () => {
  const { createRequire } = require("node:module");
  const r = createRequire(require.resolve("@firebase/rules-unit-testing"));
  const firebase = r("firebase/compat/app");
  r("firebase/compat/firestore");
  const FV = firebase.firestore.FieldValue;

  const MOD_UID = "mod_user_bf";
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const modDb = testEnv.authenticatedContext(MOD_UID, { email: "mod@bf.edu", email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("moderators").doc(MOD_UID).set({
      userId: MOD_UID,
      assignedBy: ADMIN_UID,
      createdAt: now,
    });
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      firstName: "Amina",
      lastName: "Ouédraogo",
      displayName: "Amina Ouédraogo",
      role: "ENSEIGNANT",
      country: "Burkina Faso",
      city: "Ouagadougou",
      region: "Centre",
      verified: false,
      suspended: false,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("reports").doc("rep_mod").set({
      reporterId: ALICE_UID,
      reporterName: "Amina",
      targetType: "POST",
      targetId: "post_1",
      reason: "Spam",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("verification_requests").doc("ver_mod").set({
      userId: ALICE_UID,
      userName: "Amina",
      targetType: "TEACHER",
      targetId: ALICE_UID,
      targetName: "Amina",
      documentInfo: "Attestation MENAPLN 2026",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    modDb.collection("reports").doc("rep_mod").update({
      status: "REVIEWED",
      reviewedBy: MOD_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(modDb.collection("admin_logs").get());
  await assertFails(
    modDb.collection("users").doc(ALICE_UID).update({ suspended: true, updatedAt: pastDate(1000) })
  );
  await assertFails(
    modDb.collection("verification_requests").doc("ver_mod").update({
      status: "APPROVED",
      reviewedBy: MOD_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({ suspended: true, updatedAt: pastDate(1000) })
  );

  // Oversized resource (> 20MB) is rejected
  await assertFails(
    bobDb.collection("resources").doc("res_big").set({
      title: "Fichier trop lourd",
      resourceType: "PDF",
      subject: "Physique",
      level: "Lycée",
      fileName: "gros.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 21000000,
      authorId: BOB_UID,
      authorName: "Bob",
      visibility: "PUBLIC",
      downloadsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Base64 in resource contentBody is rejected
  await assertFails(
    bobDb.collection("resources").doc("res_b64").set({
      title: "Fichier base64 interdit",
      resourceType: "PDF",
      subject: "Physique",
      level: "Lycée",
      fileName: "b64.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 1024,
      contentBody: "base64:JVBERi0xLjQ=",
      authorId: BOB_UID,
      authorName: "Bob",
      visibility: "PUBLIC",
      downloadsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid resource with Cloud Storage fileUrl & storagePath succeeds
  await assertSucceeds(
    bobDb.collection("resources").doc("res_valid").set({
      title: "Annales BAC Philo",
      resourceType: "COURS",
      subject: "Philosophie",
      level: "Terminale",
      fileName: "bac_philo.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 256000,
      fileUrl: "https://firebasestorage.googleapis.com/v0/b/profplus/o/resources%2Fbac_philo.pdf?alt=media",
      storagePath: `resources/${BOB_UID}/bac_philo.pdf`,
      contentBody: "Dissertation et commentaire...",
      authorId: BOB_UID,
      authorName: "Bob Kaboré",
      visibility: "PUBLIC",
      downloadsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Direct client increment of downloadsCount FAILS (Cloud Functions handles downloadsCount)
  await assertFails(
    bobDb.collection("resources").doc("res_valid").update({
      downloadsCount: FV.increment(1),
      updatedAt: FV.serverTimestamp(),
    })
  );

  // Recording download event in /resources/{id}/downloads SUCCEEDS
  await assertSucceeds(
    bobDb.collection("resources").doc("res_valid").collection("downloads").doc("dl_1").set({
      resourceId: "res_valid",
      userId: BOB_UID,
      createdAt: pastDate(500),
    })
  );
  // Suspended alice cannot create resources
  await assertFails(
    aliceDb.collection("resources").doc("res_alice_susp").set({
      title: "Bloqué",
      resourceType: "COURS",
      subject: "Maths",
      level: "3ème",
      fileName: "c.txt",
      mimeType: "text/plain",
      fileSizeBytes: 100,
      authorId: ALICE_UID,
      authorName: "Amina",
      visibility: "PUBLIC",
      downloadsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});
