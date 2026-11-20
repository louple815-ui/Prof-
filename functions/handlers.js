/**
 * Pure, dependency-injected Cloud Functions handlers for prof+ (ONADJA PRODUCTION).
 * Handles:
 *  1) Server-side counters: commentsCount, argumentsCount, sharesCount, downloadsCount.
 *  2) Controlled notifications: notifications collection writes via Admin SDK only,
 *     enforcing recipient preferences (users/{uid}/private/info) & blockedUserIds.
 */

const VALID_NOTIFICATION_TYPES = new Set([
  "COMMENT",
  "REPLY",
  "MENTION",
  "MESSAGE",
  "INVITATION",
  "ANNOUNCEMENT",
  "GROUP_ACTIVITY",
]);

function preferenceFieldForType(type) {
  switch (type) {
    case "COMMENT":
      return "notifyComments";
    case "REPLY":
      return "notifyReplies";
    case "MENTION":
      return "notifyMentions";
    case "MESSAGE":
      return "notifyMessages";
    case "INVITATION":
      return "notifyInvitations";
    case "ANNOUNCEMENT":
      return "notifyAnnouncements";
    case "GROUP_ACTIVITY":
      return "notifyGroups";
    default:
      return null;
  }
}

function truncate(str, max) {
  if (typeof str !== "string") return "";
  return str.trim().slice(0, max);
}

/**
 * Decrements a numeric counter atomically with a floor at 0 via transaction.
 */
async function adjustCounter(db, FieldValue, docRef, fieldName, delta) {
  if (!docRef || delta === 0) return false;
  if (delta > 0) {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(docRef);
      if (!snap.exists) return;
      const current = typeof snap.get(fieldName) === "number" ? snap.get(fieldName) : 0;
      const next = Math.max(0, current + delta);
      tx.update(docRef, {
        [fieldName]: next,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    return true;
  }
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(docRef);
    if (!snap.exists) return;
    const current = typeof snap.get(fieldName) === "number" ? snap.get(fieldName) : 0;
    const next = Math.max(0, current + delta);
    tx.update(docRef, {
      [fieldName]: next,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return true;
}

/**
 * Creates a server-controlled notification only if:
 *  - recipientId !== actorId
 *  - notification type is valid
 *  - recipient hasn't disabled that category in users/{recipientId}/private/info
 *  - recipient hasn't blocked actorId in blockedUserIds
 */
async function createControlledNotification(db, FieldValue, params) {
  const {
    recipientId,
    actorId,
    actorName,
    type,
    title,
    body,
    targetId = "",
  } = params;

  if (!recipientId || !actorId || recipientId === actorId) {
    return { created: false, reason: "SELF_OR_MISSING_IDS" };
  }
  if (!VALID_NOTIFICATION_TYPES.has(type)) {
    return { created: false, reason: "INVALID_TYPE" };
  }

  const privateSnap = await db
    .collection("users")
    .doc(recipientId)
    .collection("private")
    .doc("info")
    .get();

  if (privateSnap.exists) {
    const prefs = privateSnap.data() || {};
    const blocked = Array.isArray(prefs.blockedUserIds) ? prefs.blockedUserIds : [];
    if (blocked.includes(actorId)) {
      return { created: false, reason: "ACTOR_BLOCKED" };
    }
    const prefField = preferenceFieldForType(type);
    if (prefField && prefs[prefField] === false) {
      return { created: false, reason: "PREFERENCE_DISABLED" };
    }
  }

  const payload = {
    recipientId: truncate(recipientId, 128),
    actorId: truncate(actorId, 128),
    actorName: truncate(actorName || "Utilisateur prof+", 120),
    type,
    title: truncate(title, 140),
    body: truncate(body, 400),
    read: false,
    createdAt: FieldValue.serverTimestamp(),
  };
  if (targetId) {
    payload.targetId = truncate(targetId, 128);
  }

  const ref = db.collection("notifications").doc();
  await ref.set(payload);
  return { created: true, id: ref.id };
}

async function handleCommentCreated(db, FieldValue, comment) {
  if (!comment || comment.status !== "ACTIVE") {
    return { counterUpdated: false, notificationsCreated: 0 };
  }

  let notificationsCreated = 0;
  let counterUpdated = false;

  if (comment.targetType === "POST" && comment.targetId) {
    const postRef = db.collection("posts").doc(comment.targetId);
    counterUpdated = await adjustCounter(db, FieldValue, postRef, "commentsCount", 1);

    const postSnap = await postRef.get();
    if (postSnap.exists) {
      const post = postSnap.data() || {};
      if (post.authorId && post.authorId !== comment.authorId) {
        const res = await createControlledNotification(db, FieldValue, {
          recipientId: post.authorId,
          actorId: comment.authorId,
          actorName: comment.authorName,
          type: "COMMENT",
          title: "Nouveau commentaire",
          body: `${comment.authorName || "Un collègue"} : ${truncate(comment.content, 160)}`,
          targetId: comment.targetId,
        });
        if (res.created) notificationsCreated += 1;
      }
    }
  } else if (comment.targetType === "DEBATE" && comment.targetId) {
    const debateRef = db.collection("debates").doc(comment.targetId);
    counterUpdated = await adjustCounter(db, FieldValue, debateRef, "argumentsCount", 1);

    const debateSnap = await debateRef.get();
    if (debateSnap.exists) {
      const debate = debateSnap.data() || {};
      if (debate.authorId && debate.authorId !== comment.authorId) {
        const res = await createControlledNotification(db, FieldValue, {
          recipientId: debate.authorId,
          actorId: comment.authorId,
          actorName: comment.authorName,
          type: "COMMENT",
          title: "Nouvelle contribution au débat",
          body: `${comment.authorName || "Un collègue"} a répondu : ${truncate(comment.content, 160)}`,
          targetId: comment.targetId,
        });
        if (res.created) notificationsCreated += 1;
      }
    }
  }

  if (comment.parentCommentId) {
    const parentSnap = await db.collection("comments").doc(comment.parentCommentId).get();
    if (parentSnap.exists) {
      const parent = parentSnap.data() || {};
      if (parent.authorId && parent.authorId !== comment.authorId) {
        const res = await createControlledNotification(db, FieldValue, {
          recipientId: parent.authorId,
          actorId: comment.authorId,
          actorName: comment.authorName,
          type: "REPLY",
          title: "Réponse à votre commentaire",
          body: `${comment.authorName || "Un collègue"} : ${truncate(comment.content, 160)}`,
          targetId: comment.targetId || "",
        });
        if (res.created) notificationsCreated += 1;
      }
    }
  }

  return { counterUpdated, notificationsCreated };
}

async function handleCommentUpdated(db, FieldValue, before, after) {
  if (!before || !after) return { counterUpdated: false };
  if (before.status === after.status) return { counterUpdated: false };

  let delta = 0;
  if (before.status === "ACTIVE" && after.status !== "ACTIVE") {
    delta = -1;
  } else if (before.status !== "ACTIVE" && after.status === "ACTIVE") {
    delta = 1;
  }
  if (delta === 0) return { counterUpdated: false };

  if (after.targetType === "POST" && after.targetId) {
    const updated = await adjustCounter(db, FieldValue, db.collection("posts").doc(after.targetId), "commentsCount", delta);
    return { counterUpdated: updated, delta };
  }
  if (after.targetType === "DEBATE" && after.targetId) {
    const updated = await adjustCounter(db, FieldValue, db.collection("debates").doc(after.targetId), "argumentsCount", delta);
    return { counterUpdated: updated, delta };
  }
  return { counterUpdated: false };
}

async function handleCommentDeleted(db, FieldValue, deletedComment) {
  if (!deletedComment || deletedComment.status !== "ACTIVE") {
    return { counterUpdated: false };
  }
  if (deletedComment.targetType === "POST" && deletedComment.targetId) {
    const updated = await adjustCounter(
      db,
      FieldValue,
      db.collection("posts").doc(deletedComment.targetId),
      "commentsCount",
      -1
    );
    return { counterUpdated: updated };
  }
  if (deletedComment.targetType === "DEBATE" && deletedComment.targetId) {
    const updated = await adjustCounter(
      db,
      FieldValue,
      db.collection("debates").doc(deletedComment.targetId),
      "argumentsCount",
      -1
    );
    return { counterUpdated: updated };
  }
  return { counterUpdated: false };
}

async function handlePostShareCreated(db, FieldValue, postId) {
  if (!postId) return { counterUpdated: false };
  const updated = await adjustCounter(db, FieldValue, db.collection("posts").doc(postId), "sharesCount", 1);
  return { counterUpdated: updated };
}

async function handleResourceDownloadCreated(db, FieldValue, resourceId) {
  if (!resourceId) return { counterUpdated: false };
  const updated = await adjustCounter(
    db,
    FieldValue,
    db.collection("resources").doc(resourceId),
    "downloadsCount",
    1
  );
  return { counterUpdated: updated };
}

async function handleMessageCreated(db, FieldValue, message) {
  if (!message || !Array.isArray(message.participantIds)) {
    return { notificationsCreated: 0 };
  }
  const preview = truncate(
    message.text || (message.attachmentName ? `Pièce jointe : ${message.attachmentName}` : "Nouveau message"),
    160
  );
  let notificationsCreated = 0;
  for (const recipientId of message.participantIds) {
    if (!recipientId || recipientId === message.senderId) continue;
    const res = await createControlledNotification(db, FieldValue, {
      recipientId,
      actorId: message.senderId,
      actorName: message.senderName,
      type: "MESSAGE",
      title: `Message de ${truncate(message.senderName || "un collègue", 60)}`,
      body: preview,
      targetId: message.conversationId || "",
    });
    if (res.created) notificationsCreated += 1;
  }
  return { notificationsCreated };
}

async function handleGroupMemberWritten(db, FieldValue, before, after) {
  if (!after || !after.groupId || !after.userId) {
    return { notificationsCreated: 0 };
  }
  const groupSnap = await db.collection("groups").doc(after.groupId).get();
  if (!groupSnap.exists) return { notificationsCreated: 0 };
  const group = groupSnap.data() || {};

  // Owner invited a user
  if (!before && after.status === "INVITED" && after.userId !== group.ownerId) {
    const res = await createControlledNotification(db, FieldValue, {
      recipientId: after.userId,
      actorId: group.ownerId,
      actorName: group.ownerName || "Responsable de groupe",
      type: "INVITATION",
      title: "Invitation à un groupe",
      body: `Vous êtes invité(e) à rejoindre « ${truncate(group.name, 80)} ».`,
      targetId: after.groupId,
    });
    return { notificationsCreated: res.created ? 1 : 0 };
  }

  // Owner approved pending request
  if (before && before.status === "PENDING" && after.status === "ACTIVE" && after.userId !== group.ownerId) {
    const res = await createControlledNotification(db, FieldValue, {
      recipientId: after.userId,
      actorId: group.ownerId,
      actorName: group.ownerName || "Responsable de groupe",
      type: "GROUP_ACTIVITY",
      title: "Adhésion approuvée",
      body: `Votre demande pour « ${truncate(group.name, 80)} » a été acceptée.`,
      targetId: after.groupId,
    });
    return { notificationsCreated: res.created ? 1 : 0 };
  }

  // Member requested to join private group -> notify owner
  if (!before && after.status === "PENDING" && group.ownerId && after.userId !== group.ownerId) {
    const res = await createControlledNotification(db, FieldValue, {
      recipientId: group.ownerId,
      actorId: after.userId,
      actorName: after.userName || "Un membre",
      type: "GROUP_ACTIVITY",
      title: "Nouvelle demande d'adhésion",
      body: `${after.userName || "Un collègue"} souhaite rejoindre « ${truncate(group.name, 80)} ».`,
      targetId: after.groupId,
    });
    return { notificationsCreated: res.created ? 1 : 0 };
  }

  return { notificationsCreated: 0 };
}

module.exports = {
  adjustCounter,
  createControlledNotification,
  handleCommentCreated,
  handleCommentUpdated,
  handleCommentDeleted,
  handlePostShareCreated,
  handleResourceDownloadCreated,
  handleMessageCreated,
  handleGroupMemberWritten,
};
