const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  handleCommentCreated,
  handleCommentUpdated,
  handleCommentDeleted,
  handlePostShareCreated,
  handleResourceDownloadCreated,
  handleMessageCreated,
  handleGroupMemberWritten,
  createControlledNotification,
} = require("./handlers");

/**
 * Lightweight in-memory Firestore + FieldValue mock supporting collections,
 * subcollections, auto-ID docs, and atomic transactions.
 */
function createInMemoryFirestore(seed = {}) {
  const store = new Map(Object.entries(seed).map(([k, v]) => [k, structuredClone(v)]));
  let autoIdSeq = 0;

  const FieldValue = {
    serverTimestamp: () => "__SERVER_TS__",
  };

  function makeDocRef(path) {
    return {
      id: path.split("/").pop(),
      path,
      collection(name) {
        return makeCollectionRef(`${path}/${name}`);
      },
      async get() {
        const data = store.get(path);
        return {
          exists: data !== undefined,
          id: path.split("/").pop(),
          data: () => (data ? structuredClone(data) : undefined),
          get: (field) => (data ? data[field] : undefined),
        };
      },
      async set(payload) {
        store.set(path, structuredClone(payload));
      },
      async update(patch) {
        const current = store.get(path);
        if (!current) throw new Error(`Missing doc ${path}`);
        store.set(path, { ...current, ...structuredClone(patch) });
      },
    };
  }

  function makeCollectionRef(path) {
    return {
      doc(id) {
        const docId = id || `auto_${++autoIdSeq}`;
        return makeDocRef(`${path}/${docId}`);
      },
    };
  }

  const db = {
    collection(name) {
      return makeCollectionRef(name);
    },
    async runTransaction(fn) {
      const tx = {
        async get(ref) {
          return ref.get();
        },
        update(ref, patch) {
          const current = store.get(ref.path);
          if (!current) throw new Error(`Missing doc ${ref.path}`);
          store.set(ref.path, { ...current, ...structuredClone(patch) });
        },
      };
      return fn(tx);
    },
    _dump(path) {
      return store.get(path);
    },
    _allWithPrefix(prefix) {
      return [...store.entries()]
        .filter(([k]) => k.startsWith(prefix))
        .map(([k, v]) => ({ path: k, data: v }));
    },
  };

  return { db, FieldValue };
}

test("Comment creation increments post commentsCount and creates controlled notification", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "posts/post_1": {
      authorId: "alice",
      commentsCount: 0,
      status: "ACTIVE",
    },
    "users/alice/private/info": {
      userId: "alice",
      notifyComments: true,
      blockedUserIds: [],
    },
  });

  const res = await handleCommentCreated(db, FieldValue, {
    targetId: "post_1",
    targetType: "POST",
    authorId: "bob",
    authorName: "Bob Kaboré",
    content: "Excellente fiche pédagogique !",
    status: "ACTIVE",
  });

  assert.equal(res.counterUpdated, true);
  assert.equal(res.notificationsCreated, 1);
  assert.equal(db._dump("posts/post_1").commentsCount, 1);

  const notifs = db._allWithPrefix("notifications/");
  assert.equal(notifs.length, 1);
  assert.equal(notifs[0].data.recipientId, "alice");
  assert.equal(notifs[0].data.type, "COMMENT");
  assert.equal(notifs[0].data.read, false);
});

test("Debate comment increments argumentsCount, and reply notifies parent comment author", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "debates/deb_1": {
      authorId: "alice",
      argumentsCount: 2,
      status: "OPEN",
    },
    "comments/c_parent": {
      authorId: "claire",
      status: "ACTIVE",
    },
  });

  const res = await handleCommentCreated(db, FieldValue, {
    targetId: "deb_1",
    targetType: "DEBATE",
    parentCommentId: "c_parent",
    authorId: "bob",
    authorName: "Bob",
    content: "Je suis d'accord avec Claire.",
    status: "ACTIVE",
  });

  assert.equal(res.counterUpdated, true);
  assert.equal(res.notificationsCreated, 2);
  assert.equal(db._dump("debates/deb_1").argumentsCount, 3);
  const notifs = db._allWithPrefix("notifications/").map((n) => n.data);
  assert.ok(notifs.some((n) => n.recipientId === "alice" && n.type === "COMMENT"));
  assert.ok(notifs.some((n) => n.recipientId === "claire" && n.type === "REPLY"));
});

test("Moderation removal and deletion decrement commentsCount safely floored at 0", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "posts/post_mod": {
      authorId: "alice",
      commentsCount: 1,
      status: "ACTIVE",
    },
  });

  await handleCommentUpdated(
    db,
    FieldValue,
    { targetId: "post_mod", targetType: "POST", status: "ACTIVE" },
    { targetId: "post_mod", targetType: "POST", status: "REMOVED" }
  );
  assert.equal(db._dump("posts/post_mod").commentsCount, 0);

  // Additional delete of an ACTIVE comment should not drop below 0
  await handleCommentDeleted(db, FieldValue, {
    targetId: "post_mod",
    targetType: "POST",
    status: "ACTIVE",
  });
  assert.equal(db._dump("posts/post_mod").commentsCount, 0);
});

test("Post share and resource download triggers increment sharesCount and downloadsCount server-side", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "posts/post_s": { sharesCount: 4 },
    "resources/res_d": { downloadsCount: 9 },
  });

  await handlePostShareCreated(db, FieldValue, "post_s");
  await handleResourceDownloadCreated(db, FieldValue, "res_d");

  assert.equal(db._dump("posts/post_s").sharesCount, 5);
  assert.equal(db._dump("resources/res_d").downloadsCount, 10);
});

test("Controlled notifications suppress self-notifications, disabled preferences, and blocked users", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "users/alice/private/info": {
      notifyMessages: false,
      blockedUserIds: [],
    },
    "users/bob/private/info": {
      notifyMessages: true,
      blockedUserIds: ["eve_spam"],
    },
    "users/claire/private/info": {
      notifyMessages: true,
      blockedUserIds: [],
    },
  });

  // Self-notification is suppressed
  const selfRes = await createControlledNotification(db, FieldValue, {
    recipientId: "claire",
    actorId: "claire",
    actorName: "Claire",
    type: "MESSAGE",
    title: "Self",
    body: "Should not send",
  });
  assert.equal(selfRes.created, false);

  // Message from eve_spam to alice (disabled), bob (blocked), claire (allowed), eve_spam (self)
  const msgRes = await handleMessageCreated(db, FieldValue, {
    conversationId: "conv_grp",
    participantIds: ["alice", "bob", "claire", "eve_spam"],
    senderId: "eve_spam",
    senderName: "Eve",
    text: "Bonjour à tous",
  });

  assert.equal(msgRes.notificationsCreated, 1);
  const notifs = db._allWithPrefix("notifications/");
  assert.equal(notifs.length, 1);
  assert.equal(notifs[0].data.recipientId, "claire");
  assert.equal(notifs[0].data.type, "MESSAGE");
});

test("Group member lifecycle emits invitation, pending join request, and approval notifications", async () => {
  const { db, FieldValue } = createInMemoryFirestore({
    "groups/grp_1": {
      name: "Maths Burkina",
      ownerId: "alice",
      ownerName: "Amina",
    },
  });

  // 1. Bob requests to join private group -> notifies owner alice
  const reqRes = await handleGroupMemberWritten(db, FieldValue, null, {
    groupId: "grp_1",
    userId: "bob",
    userName: "Bob Kaboré",
    status: "PENDING",
  });
  assert.equal(reqRes.notificationsCreated, 1);

  // 2. Owner approves Bob -> notifies bob
  const approveRes = await handleGroupMemberWritten(
    db,
    FieldValue,
    { groupId: "grp_1", userId: "bob", status: "PENDING" },
    { groupId: "grp_1", userId: "bob", status: "ACTIVE" }
  );
  assert.equal(approveRes.notificationsCreated, 1);

  // 3. Owner invites Claire -> notifies claire
  const invRes = await handleGroupMemberWritten(db, FieldValue, null, {
    groupId: "grp_1",
    userId: "claire",
    userName: "Claire",
    status: "INVITED",
  });
  assert.equal(invRes.notificationsCreated, 1);

  const notifs = db._allWithPrefix("notifications/").map((n) => n.data);
  assert.ok(notifs.some((n) => n.recipientId === "alice" && n.type === "GROUP_ACTIVITY"));
  assert.ok(notifs.some((n) => n.recipientId === "bob" && n.type === "GROUP_ACTIVITY"));
  assert.ok(notifs.some((n) => n.recipientId === "claire" && n.type === "INVITATION"));
});
