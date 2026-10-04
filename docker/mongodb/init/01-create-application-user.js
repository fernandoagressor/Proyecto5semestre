const databaseName = process.env.MONGO_APP_DATABASE;
const applicationUser = process.env.MONGO_APP_USER;
const applicationPassword = process.env.MONGO_APP_PASSWORD;
const migratorUser = process.env.MONGO_MIGRATOR_USER;
const migratorPassword = process.env.MONGO_MIGRATOR_PASSWORD;

if (
  !databaseName ||
  !applicationUser ||
  !applicationPassword ||
  !migratorUser ||
  !migratorPassword
) {
  throw new Error(
    "Las credenciales de aplicación y migración de MongoDB son obligatorias"
  );
}

const applicationDatabase = db.getSiblingDB(databaseName);

if (!applicationDatabase.getUser(applicationUser)) {
  applicationDatabase.createUser({
    user: applicationUser,
    pwd: applicationPassword,
    roles: [{ role: "readWrite", db: databaseName }]
  });
} else {
  applicationDatabase.updateUser(applicationUser, {
    pwd: applicationPassword,
    roles: [{ role: "readWrite", db: databaseName }]
  });
}

const adminDatabase = db.getSiblingDB("admin");

if (!adminDatabase.getUser(migratorUser)) {
  adminDatabase.createUser({
    user: migratorUser,
    pwd: migratorPassword,
    roles: [{ role: "readWrite", db: databaseName }]
  });
} else {
  adminDatabase.updateUser(migratorUser, {
    pwd: migratorPassword,
    roles: [{ role: "readWrite", db: databaseName }]
  });
}

print(`Usuarios de aplicación y migración creados para la base ${databaseName}`);
