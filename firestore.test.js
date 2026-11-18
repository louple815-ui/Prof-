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

test("User registration, PII split isolation, and RBAC escalation prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@bf.edu", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@bf.edu", email_verified: true }).firestore();
  const now = pastDate();

  // Valid teacher profile creation
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

test("Posts, reactions, comments, and query filter alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Amina Ouédraogo",
      authorRole: "ENSEIGNANT",
      authorVerified: false,
      content: "Nouvelle fiche de mathématiques pour la classe de 3ème au Burkina Faso.",
      category: "Méthodes pédagogiques",
      visibility: "PUBLIC",
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

  // Bob cannot edit Alice's post content, but can increment likesCount by +1 with reaction
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
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: 1,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("reactions").doc(BOB_UID).set({
      postId: "post_1",
      userId: BOB_UID,
      type: "LIKE",
      createdAt: pastDate(1000),
    })
  );
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
});

test("Debates, polls, and atomic batch voting", async () => {
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

test("Conversations and messages strict participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_intruder").firestore();
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

  await assertSucceeds(
    bobDb.collection("messages").doc("msg_1").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob Kaboré",
      text: "Bonjour Amina, voici la fiche.",
      createdAt: pastDate(1000),
    })
  );

  // Charlie (non-participant) cannot read conversation or messages or inject a message
  await assertFails(charlieDb.collection("conversations").doc("conv_1").get());
  await assertFails(charlieDb.collection("messages").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(
    charlieDb.collection("messages").doc("msg_intrude").set({
      conversationId: "conv_1",
      participantIds: [ALICE_UID, BOB_UID, "charlie_intruder"],
      senderId: "charlie_intruder",
      senderName: "Charlie",
      text: "Intrusion",
      createdAt: pastDate(1000),
    })
  );

  // Bob can query conversations and messages he participates in
  await assertSucceeds(bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get());
  await assertSucceeds(bobDb.collection("messages").where("participantIds", "array-contains", BOB_UID).get());
});

test("Groups, resources, establishments, notifications, reports, and verifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  // Group + owner membership
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_math").set({
      name: "Maths Burkina",
      description: "Échanges entre professeurs de mathématiques.",
      domain: "Matière",
      subject: "Mathématiques",
      level: "Secondaire",
      region: "Centre",
      privacy: "PUBLIC",
      rules: "Respect et entraide professionnelle.",
      ownerId: ALICE_UID,
      ownerName: "Amina Ouédraogo",
      membersCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_members").doc(`grp_math_${ALICE_UID}`).set({
      groupId: "grp_math",
      userId: ALICE_UID,
      userName: "Amina Ouédraogo",
      userRole: "ENSEIGNANT",
      memberRole: "OWNER",
      status: "ACTIVE",
      visibility: "PUBLIC",
      joinedAt: now,
    })
  );

  // Pedagogical resource
  await assertSucceeds(
    aliceDb.collection("resources").doc("res_1").set({
      title: "Cours Fonctions Affines 3ème",
      description: "Fiche pédagogique complète avec exercices et corrigé.",
      resourceType: "FICHE_PEDAGOGIQUE",
      subject: "Mathématiques",
      level: "3ème",
      fileName: "fonctions_affines_3e.txt",
      mimeType: "text/plain",
      fileSizeBytes: 1240,
      contentBody: "Objectifs pédagogiques...",
      authorId: ALICE_UID,
      authorName: "Amina Ouédraogo",
      visibility: "PUBLIC",
      downloadsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

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

  // Notification to Bob
  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").set({
      recipientId: BOB_UID,
      actorId: ALICE_UID,
      actorName: "Amina Ouédraogo",
      type: "COMMENT",
      title: "Nouveau commentaire",
      body: "Amina a commenté votre publication.",
      targetId: "post_1",
      read: false,
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("notifications").where("recipientId", "==", BOB_UID).get());
  await assertSucceeds(bobDb.collection("notifications").where("recipientId", "==", BOB_UID).get());
  await assertSucceeds(bobDb.collection("notifications").doc("notif_1").update({ read: true }));

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
