import { MongoClient } from "mongodb";

const MONGODB_URI = process.env.MONGODB_URI || "mongodb://localhost:27017";

let client = null;

/**
 * Returns the shared MongoClient, connecting on first call.
 * The MCP server process is single-threaded so no locking is needed.
 */
export async function getMongoClient() {
  if (!client) {
    client = new MongoClient(MONGODB_URI, {
      connectTimeoutMS: 5000,
      serverSelectionTimeoutMS: 5000,
    });
    await client.connect();
  }
  return client;
}

/** Convenience: get a collection from the named database. */
export async function collection(dbName, collectionName) {
  const c = await getMongoClient();
  return c.db(dbName).collection(collectionName);
}

/** Gracefully close on process exit. */
process.on("exit", () => {
  if (client) client.close();
});
