const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");

const {
  handleCommentCreated,
  handleCommentUpdated,
  handleCommentDeleted,
  handlePostShareCreated,
  handleResourceDownloadCreated,
  handleMessageCreated,
  handleGroupMemberWritten,
} = require("./handlers");

initializeApp();

const DATABASE_ID = "ai-studio-android-prof-ba263d8f-d3ab-4a71-b325-29ccd86045ee";
const REGION = "europe-west1";
const db = getFirestore(DATABASE_ID);

exports.onCommentCreated = onDocumentCreated(
  {
    document: "comments/{commentId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const data = event.data?.data();
    await handleCommentCreated(db, FieldValue, data);
  }
);

exports.onCommentUpdated = onDocumentUpdated(
  {
    document: "comments/{commentId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    await handleCommentUpdated(db, FieldValue, before, after);
  }
);

exports.onCommentDeleted = onDocumentDeleted(
  {
    document: "comments/{commentId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const data = event.data?.data();
    await handleCommentDeleted(db, FieldValue, data);
  }
);

exports.onPostShareCreated = onDocumentCreated(
  {
    document: "posts/{postId}/shares/{shareId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    await handlePostShareCreated(db, FieldValue, event.params.postId);
  }
);

exports.onResourceDownloadCreated = onDocumentCreated(
  {
    document: "resources/{resourceId}/downloads/{downloadId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    await handleResourceDownloadCreated(db, FieldValue, event.params.resourceId);
  }
);

exports.onMessageCreated = onDocumentCreated(
  {
    document: "messages/{messageId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const data = event.data?.data();
    await handleMessageCreated(db, FieldValue, data);
  }
);

exports.onGroupMemberWritten = onDocumentWritten(
  {
    document: "group_members/{memberDocId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const before = event.data?.before?.exists ? event.data.before.data() : null;
    const after = event.data?.after?.exists ? event.data.after.data() : null;
    await handleGroupMemberWritten(db, FieldValue, before, after);
  }
);
